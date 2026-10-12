package com.github.gaohongf.message.template;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.gaohongf.message.dao.MessageTemplateDao;
import com.github.gaohongf.message.entity.po.MessageTemplateEntity;
import com.github.gaohongf.message.mq.MessageHandlerType;

import lombok.AllArgsConstructor;

/**
 * 模板的进程内缓存。
 *
 * <h2>为什么要缓存</h2>
 * 每条消息都要查一次模板, 而模板几乎不变 —— 这是最典型的"读多写零"场景。
 * 加一层本地缓存后, 消费一条消息只碰一次数据库（那一次是落库, 必须的）。
 *
 * <h2>为什么不走 {@code @Cacheable}(Redis)</h2>
 * 模板是"配置", 一个进程里几 KB 的量, 为一个进程内的只读数据付一次网络往返
 * （而且是每条消息一次）没有意义。真要手动同步失效时, 本地的 TTL 也就等几分钟。
 *
 * <h2>改了模板多久生效</h2>
 * 最多 {@link #TTL} 分钟。这是<b>刻意接受</b>的延迟, 不是遗漏: 改模板的人是在改文案,
 * 而不是在救火 —— 需要立即生效的场景至今没有。反过来, 模板缺失（或写错占位符）是
 * <b>响亮的 ERROR 日志</b>（见 {@link #find} 与 {@link TemplateRenderer}）,
 * 所以"改了没生效"不会变成一个查不出原因的玄学问题。
 *
 * <h2>为什么"查不到"也要缓存</h2>
 * 模板缺失时每条消息都会去查一次库（而缺失恰恰是持续的: 生产方写错了键,
 * 它会一直发过来）。把 {@code Optional.empty()} 缓存住, 数据库就不用替别人的配置
 * 错误买单。Caffeine 不接受 null value, 所以这里包成 Optional。
 */
@AllArgsConstructor
@Component
public class MessageTemplateCache {

    /** 本地副本的存活时间。见类注释: 这是刻意接受的生效延迟 */
    static final Duration TTL = Duration.ofMinutes(5);

    private static final int MAX_SIZE = 500;

    private final MessageTemplateDao messageTemplateDao;

    /** key 是 {@link #key}, value 是查询结果（含"没有这条模板"） */
    private final Cache<String, Optional<MessageTemplateEntity>> cache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterWrite(TTL)
            .recordStats()
            .build();

    /**
     * 查一条<b>启用中</b>的模板。
     *
     * @return 空表示"没有这条模板"或"模板已停用"。两种情况的后果一样（这条消息发不出去）,
     *         所以不做区分 —— 区分开只会让调用方多写一个 if, 而没有任何不同的动作
     */
    public Optional<MessageTemplateEntity> find(String templateKey, MessageHandlerType channel) {
        if (templateKey == null || templateKey.isBlank() || channel == null) {
            // 空键不该走到这里（发布器已经挡了一道）, 真到了就当成"没有",
            // 免得在缓存里塞一堆以 "null" 为键的条目
            return Optional.empty();
        }
        return cache.get(key(templateKey, channel), ignored -> load(templateKey, channel));
    }

    private Optional<MessageTemplateEntity> load(String templateKey, MessageHandlerType channel) {
        // 条件构造器里 enabled = 1 与 @TableLogic 的 deleted = 0 都会自动带上,
        // 所以"停用"和"已删除"的模板都不会被查出来
        return Optional.ofNullable(messageTemplateDao.selectOne(Wrappers.<MessageTemplateEntity>lambdaQuery()
                .eq(MessageTemplateEntity::getTemplateKey, templateKey)
                .eq(MessageTemplateEntity::getChannel, channel)
                .eq(MessageTemplateEntity::getEnabled, 1)));
    }

    /**
     * 缓存键。
     * <p>
     * 带上渠道不能省: 唯一键是 {@code (template_key, channel)}, 只按 key 缓存会让
     * 站内信模板在邮件那条路上被复用（或者反过来）, 而两者本来就该是两套措辞。
     */
    private static String key(String templateKey, MessageHandlerType channel) {
        return templateKey + '|' + channel.name();
    }
}
