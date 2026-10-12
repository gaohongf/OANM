package com.github.gaohongf.message.entity.res;

import java.time.LocalDateTime;

import com.github.gaohongf.message.entity.po.NotificationEntity;

/**
 * 收件箱里的一条消息。列表接口与 SSE 推送用的是<b>同一个</b>形状。
 *
 * <h2>为什么推送和列表共用一个形状</h2>
 * 前端只有一个 store、一份去重逻辑（按 id）。如果推送带一套字段、列表带另一套,
 * 前端就得为"推来的这条怎么塞进列表"写一段适配, 而那段适配正是最容易漏字段的地方
 * （典型症状: 列表里时间正常、推来的那条时间是空的）。
 *
 * <h2>为什么没有 {@code @User} 的发送人字段</h2>
 * {@code @User} 的解析是<b>序列化期的一次阻塞远程调用</b>（见 {@code ResolvingSerializer}）,
 * 在反应式栈上正是 e67bfed 修掉的那类问题。而且站内信要展示的是"发生了什么"，
 * 发送人昵称需要时由生产方当模板参数传进来（那时它已经是文案的一部分, 不需要回源）。
 *
 * <h2>为什么 id 是 String 而不是 Long</h2>
 * 理由与 {@code WorkOrderRes} 上那段相同: 主键是雪花 id(19 位), JS 的 Number 精确到
 * {@code 2^53 - 1} 就到底了, 序列化成 JSON 数字后前端拿到的是<b>已经变了的值</b>——
 * 而这个 id 正是前端列表去重的键(SSE 推来的和轮询拉到的按它合并), 丢精度会让两条
 * 不同的消息看起来是同一条。
 *
 * @param id         消息 id
 * @param type       模板键, 前端可据此决定点击后跳到哪
 * @param title      渲染快照
 * @param content    渲染快照
 * @param bizId      业务 id(如工单 id), 为空表示这条消息没有跳转目标
 * @param readFlag   0-未读 1-已读
 * @param readTime   已读时间, 未读为 null
 * @param createTime 消息产生时间
 */
public record NotificationRes(
        String id,
        String type,
        String title,
        String content,
        String bizId,
        Integer readFlag,
        LocalDateTime readTime,
        LocalDateTime createTime) {

    public static NotificationRes from(NotificationEntity entity) {
        // id 一定有值: 调用方拿到的实体要么刚插入完(ASSIGN_ID 在插入前就把雪花 id 填回去了),
        // 要么是查出来的行。所以这里不需要为 null 准备一条分支。
        return new NotificationRes(
                String.valueOf(entity.getId()),
                entity.getType(),
                entity.getTitle(),
                entity.getContent(),
                entity.getBizId(),
                entity.getReadFlag(),
                entity.getReadTime(),
                entity.getCreateTime());
    }
}
