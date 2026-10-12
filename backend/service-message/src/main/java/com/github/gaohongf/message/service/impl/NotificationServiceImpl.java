package com.github.gaohongf.message.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.message.channel.MessageChannelSender;
import com.github.gaohongf.message.dao.NotificationDao;
import com.github.gaohongf.message.entity.po.MessageTemplateEntity;
import com.github.gaohongf.message.entity.po.NotificationEntity;
import com.github.gaohongf.message.entity.req.NotificationReadState;
import com.github.gaohongf.message.entity.res.NotificationRes;
import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.message.service.NotificationService;
import com.github.gaohongf.message.template.MessageTemplateCache;
import com.github.gaohongf.message.template.RenderedContent;
import com.github.gaohongf.message.template.TemplateRenderException;
import com.github.gaohongf.message.template.TemplateRenderer;
import com.github.gaohongf.model.PageRes;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link NotificationService} 的实现。
 *
 * <h2>这里的方法都在能阻塞的线程上被调用</h2>
 * 消费路径在 Kafka 容器线程上, 查询路径在 WebFlux 的 {@code boundedElastic} 上
 * （鉴权适配器把整段处理挪到了那里, 见 {@code ReactiveAuthorizationRequestMappingHandlerAdapter}）。
 * 所以下面直接调 JDBC, 不需要包 {@code Mono.fromCallable(...).subscribeOn(...)} ——
 * 那只会让"这里到底能不能阻塞"这件事变得更难判断。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int READ = 1;
    private static final int UNREAD = 0;

    private final NotificationDao notificationDao;
    private final MessageTemplateCache templateCache;
    private final TemplateRenderer templateRenderer;
    private final List<MessageChannelSender> channelSenders;

    /**
     * 渠道实现由 Spring 注入全部, 按 {@link MessageChannelSender#supports()} 分发。
     * <p>
     * 用注入的 List 而不是 {@code Map<枚举, 实现>}: 后者要在构造器里手工拼,
     * 加一个渠道就得记得改那个拼装代码 —— 而"忘了加"的症状是新渠道静默不走。
     * 注入 List 之后, 加一个 {@code @Component} 实现就自动生效。
     */
    public NotificationServiceImpl(NotificationDao notificationDao,
            MessageTemplateCache templateCache,
            TemplateRenderer templateRenderer,
            List<MessageChannelSender> channelSenders) {
        this.notificationDao = notificationDao;
        this.templateCache = templateCache;
        this.templateRenderer = templateRenderer;
        this.channelSenders = channelSenders;
    }

    @Override
    public void handle(MessageCreateCommand command) {
        if (command == null) {
            log.error("收到空的消息需求, 已丢弃");
            return;
        }
        // 这三个字段缺一个, 这条消息就无法成立:
        // 没有 eventId 幂等失效（会重复）, 没有 receiver 不知道给谁, 没有 templateKey 不知道渲染什么。
        if (!StringUtils.hasText(command.getEventId()) || command.getReceiver() == null
                || !StringUtils.hasText(command.getTemplateKey())) {
            log.error("消息需求缺少必填字段(eventId/receiver/templateKey), 已丢弃。command={}", command);
            return;
        }

        // 渠道缺省按站内信: 生产方的发布器总是显式设置它, 缺省只是防御 ——
        // 老版本生产方发来的消息里可能没有这个字段。
        MessageHandlerType channel = command.getHandlerType() == null
                ? MessageHandlerType.APP
                : command.getHandlerType();

        MessageChannelSender sender = findSender(channel);
        if (sender == null) {
            log.error("没有渠道实现, 这条消息未发出。channel={} eventId={} templateKey={}",
                    channel, command.getEventId(), command.getTemplateKey());
            return;
        }

        Optional<MessageTemplateEntity> found = templateCache.find(command.getTemplateKey(), channel);
        if (found.isEmpty()) {
            // 常见的三个原因: 生产方写错了键、模板还没建、模板被停用了。都不会自己变好,
            // 所以不重试（重试只会把同一条错误日志刷 N 遍）; 打印出键名, 一眼能对上种子脚本。
            log.error("模板不存在或已停用, 这条消息未发出。templateKey={} channel={} eventId={}",
                    command.getTemplateKey(), channel, command.getEventId());
            return;
        }

        MessageTemplateEntity template = found.get();
        Map<String, Object> args = command.getArgs();
        RenderedContent content;
        try {
            // 标题和正文一起渲染: 两者要么都成, 要么都不落库（见 TemplateRenderer 的契约）。
            content = new RenderedContent(
                    templateRenderer.render(template.getTitleTemplate(), args),
                    templateRenderer.render(template.getContentTemplate(), args));
        } catch (TemplateRenderException failure) {
            // fail-closed: 宁可用户收不到这条通知, 也不给他发一条写着 null 或者半个句子的 ——
            // 后者会被当成"业务出错了", 而那是一条查不出原因的错误信息。
            // 这类失败只可能是配置或代码错误（占位符名字对不上）, 重试没有意义。
            log.error("模板渲染失败, 这条消息未落库。templateKey={} channel={} eventId={} 原因={}",
                    command.getTemplateKey(), channel, command.getEventId(), failure.getMessage());
            return;
        }

        sender.send(command, content);
    }

    @Override
    public PageRes<NotificationRes> page(Long receiver, long current, long size, NotificationReadState readState) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        // readState 允许为 null（内部调用方省事）, 与传 ALL 同义
        NotificationReadState state = readState == null ? NotificationReadState.ALL : readState;

        IPage<NotificationEntity> page = notificationDao.selectPage(new Page<>(safeCurrent, safeSize),
                Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getReceiver, receiver)
                        // 条件成立才拼进 SQL: 传 null 给 eq 会生成 `read_flag = null`,
                        // 那是个永远为 false 的条件, 表现为"全部"这个筛选返回空列表
                        .eq(state == NotificationReadState.UNREAD, NotificationEntity::getReadFlag, UNREAD)
                        .eq(state == NotificationReadState.READ, NotificationEntity::getReadFlag, READ)
                        // 按 id 而不是 create_time 排序: 雪花 id 单调递增（等价于按时间倒序）,
                        // 而且是唯一键 —— 用 create_time 会在同一毫秒内产生多条时出现
                        // "翻页漏掉/重复"（并列值的顺序在两次查询之间不保证稳定）。
                        .orderByDesc(NotificationEntity::getId));

        List<NotificationRes> records = page.getRecords().stream().map(NotificationRes::from).toList();
        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public long unreadCount(Long receiver) {
        return notificationDao.selectCount(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getReceiver, receiver)
                .eq(NotificationEntity::getReadFlag, UNREAD));
    }

    @Override
    public void markRead(Long receiver, Long id) {
        if (receiver == null || id == null) {
            return;
        }
        // 带着 receiver 更新, 这是防越权的关键一步: 没有它, 换个 id 就能标记别人的消息。
        // 用实体承载要改的列（而不是 set 语句连缀）是为了让 AuditMetaObjectHandler
        // 自动补上 update_time / update_by —— update(null, wrapper) 那条路拿不到审计填充。
        NotificationEntity change = new NotificationEntity();
        change.setReadFlag(READ);
        change.setReadTime(LocalDateTime.now());

        int updated = notificationDao.update(change, Wrappers.<NotificationEntity>lambdaUpdate()
                .eq(NotificationEntity::getId, id)
                .eq(NotificationEntity::getReceiver, receiver)
                // already read 的再点一次不该刷新已读时间: 否则"什么时候读的"这个字段
                // 记录的就成了"最后一次点击", 两者在排查"用户到底看没看过"时不是一回事
                .eq(NotificationEntity::getReadFlag, UNREAD));

        if (updated == 0) {
            // 两种可能: 这条已经是已读, 或者它压根不是这个人的。都不需要报错 —— 调用方
            // （前端）接下来的动作一样: 刷新列表。记 debug 是为了排查前端传错 id 的情况。
            log.debug("没有需要标记的消息(已读或不属于该收件人)。receiver={} id={}", receiver, id);
        }
    }

    @Override
    public int markAllRead(Long receiver) {
        if (receiver == null) {
            return 0;
        }
        NotificationEntity change = new NotificationEntity();
        change.setReadFlag(READ);
        change.setReadTime(LocalDateTime.now());

        return notificationDao.update(change, Wrappers.<NotificationEntity>lambdaUpdate()
                .eq(NotificationEntity::getReceiver, receiver)
                .eq(NotificationEntity::getReadFlag, UNREAD));
    }

    private MessageChannelSender findSender(MessageHandlerType channel) {
        for (MessageChannelSender sender : channelSenders) {
            if (sender.supports() == channel) {
                return sender;
            }
        }
        return null;
    }
}
