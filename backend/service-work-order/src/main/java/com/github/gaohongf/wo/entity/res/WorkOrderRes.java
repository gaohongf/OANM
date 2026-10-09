package com.github.gaohongf.wo.entity.res;

import java.time.LocalDateTime;

import com.github.gaohongf.serializer.annotation.User;

import lombok.Data;

/**
 * 工单列表行。
 *
 * <h2>为什么 id 是 String 而不是 Long</h2>
 * 主键是雪花 ID（19 位），而 JavaScript 的 {@code Number} 只能精确表示到
 * {@code 2^53 - 1}（16 位）。序列化成 JSON 数字后，前端拿到的是一个<b>已经变了的值</b> ——
 * 而且它看起来完全正常，直到拿它去查详情时才发现查不到。序列化成字符串是唯一安全的做法。
 * 同理，本接口的建单返回值也是 String。
 *
 * <h2>为什么这里没有描述、解决方法这类长文本</h2>
 * {@code solution_detail} 是模型生成的正文，可能上千字。列表一页 10 行就是十几 KB 的
 * 无用载荷，而它们要打开抽屉才看得到。所以列表只给摘要，正文由
 * {@link WorkOrderDetailRes} 按 id 单独取。
 *
 * <h2>{@link User} 标在 createBy 上</h2>
 * 库里只存 id，序列化时才换成用户对象（见 common 的 {@code ResolveStrategyRegistry}）：
 * 解析成功是 {@code {"id":1,"username":"alice"}}，用户服务不可用时降级成 {@code {"id":1}} ——
 * 不会因为取不到用户名就让整个列表 500。前端对应地要允许字段缺失。
 */
@Data
public class WorkOrderRes {

    private String id;

    private String title;

    /** 工单类型: DEMAND / FAULT */
    private String type;

    /** 优先级: LOW / MEDIUM / HIGH / URGENT */
    private String priority;

    /** 状态: NEW / ACCEPTED / RESOLVED / CLOSED */
    private String status;

    /**
     * 创建人。username 默认开启, nickname 默认关闭。
     * <p>
     * 注意这是<b>雪花 ID</b>，而 common 的用户解析器会把它输出成 JSON 数字 ——
     * 在 JS 里超过 2^53 会丢精度。展示上无碍（用户名才是给人看的），
     * 但不要拿这里解析出来的 id 当标识去查别的东西。
     */
    @User
    private Long createBy;

    private LocalDateTime createTime;

    /** AI 会话 ID，列表里也给出来，方便一眼看出哪些单子是从 AI 对话来的 */
    private String aiConversationId;
}
