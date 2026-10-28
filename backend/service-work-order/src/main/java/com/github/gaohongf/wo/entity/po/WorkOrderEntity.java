package com.github.gaohongf.wo.entity.po;

import java.time.LocalDateTime;

import org.apache.ibatis.type.EnumTypeHandler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 运维工单。
 *
 * <h2>字段与 AI 输出的对应关系</h2>
 * 这一版的字段形状是照着 {@code service-ai} 的"辅助填单"接口逐行对齐的。模型每吐一行
 * 单字段 JSON，就对应这里的一列：
 *
 * <pre>
 *   AI 输出行                        实体字段
 *   ─────────────────────────────   ──────────────────────────────
 *   {"title": "..."}                title
 *   {"type": "demand|fault"}        type
 *   {"priority": "low|..."}         priority
 *   {"content": "..."}              problemDescription
 *   {"user_choose": 2}              aiSelectedOptionId
 *   {"user_input_0": "..."}         originalProblemDescription（多行, 一行一轮）
 * </pre>
 *
 * 刻意<b>不重命名</b>：字段名与模型输出保持一致，排查时不用在脑子里做一次映射，
 * "AI 给了什么、库里存了什么"一眼能对上。
 *
 * <h2>会话 ID 只是指针</h2>
 * {@link #aiConversationId} 指向 service-ai 的 {@code spring_ai_chat_memory}，不是对话副本。
 * 见 {@code db/schema.sql} 里那一列的注释。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "work_orders", autoResultMap = true)
public class WorkOrderEntity extends BaseEntity {

    /**
     * 编号
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 工单标题
     */
    private String title;

    /**
     * 工单类型: 需求还是故障
     */
    @TableField(typeHandler = EnumTypeHandler.class)
    private WorkOrderType type;

    /**
     * 优先级
     */
    @TableField(typeHandler = EnumTypeHandler.class)
    private WorkOrderPriority priority;

    /**
     * 经过 AI 整理与明确的问题描述
     */
    private String problemDescription;

    /**
     * 用户输入的原始问题描述, 一行一轮。
     * <p>
     * 用户在意图推断页可以反复补充，每补充一次多一行，顺序与补充顺序一致。
     * 刻意用 {@code \n} 分隔的一段文本而不是一张子表：这些行没有各自独立的生命周期，
     * 从不单独查询或修改。
     */
    private String originalProblemDescription;

    /**
     * 是否为自助解决记录。
     * <p>
     * 用户依据 AI 给出的知识库建议自行处理完毕后创建。建单时状态直接是
     * {@link WorkOrderStatus#RESOLVED}，受理人是建单人本人；其余字段与普通工单一致。
     */
    private Boolean selfResolved;

    /**
     * AI 会话 ID, 归档指针。
     * <p>
     * 拿去查 {@code spring_ai_chat_memory} 能把整段对话捞回来。见 {@code db/schema.sql}。
     */
    private String aiConversationId;

    /**
     * 用户在意图推断里选中的选项 id。
     * <p>
     * 有了它才能回答"这个工单是从哪条推测来的" —— 光有会话 ID 只知道是哪次对话，
     * 不知道用户当时认可的是哪一条。
     */
    private Integer aiSelectedOptionId;

    /**
     * 受理人
     */
    private Long assigneeId;

    /**
     * 受理人对这个问题的笔记。
     * <p>
     * 结单时写入"本次心得"；取消流程不碰它。
     */
    private String assigneeNote;

    /**
     * 取消申请时间。
     * <p>
     * {@code null} 表示没有待确认的取消申请。非空时状态仍是 {@link WorkOrderStatus#ACCEPTED}，
     * 由受理人决定同意（状态改为 {@code CLOSED}）或拒绝（清空这个时间）。刻意不新增一个状态值：
     * 取消申请不是工单生命周期里的一个阶段，而是"已受理"之上挂起的一次待办。
     */
    private LocalDateTime cancelRequestedAt;

    /**
     * 此单状态。
     * <p>
     * 普通工单建单时由服务端固定为 {@link WorkOrderStatus#NEW}；
     * 自助解决记录由服务端固定为 {@link WorkOrderStatus#RESOLVED}。两种情况都不接受调用方指定。
     */
    @TableField(typeHandler = EnumTypeHandler.class)
    private WorkOrderStatus status;
}
