package com.github.gaohongf.ai.entity.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 浏览器提交工单时发来的内容。
 *
 * <p>
 * 只收用户最终确认/编辑过的字段；{@code aiConversationId} 与
 * {@code aiSelectedOptionId} 由服务端从 {@link #conversationId} / {@link #userSelect}
 * 映射过去，不接受浏览器自报，避免归档信息与实际会话对不上。
 *
 * <p>
 * 校验写在 service 里而不是注解上：service-ai 没有引入 validation starter，
 * 而这里的规则都是"非空"级别，手写反而更直接。
 */
@Data
public class AiWorkOrderSubmitCommand {

    @Schema(description = "生成这张草稿的 AI 会话 id")
    private String conversationId;

    @Schema(description = "用户选中的意图选项编号；自助解决记录为空")
    private Integer userSelect;

    @Schema(description = "是否自助解决记录。true 时工单服务直接记为已解决，受理人为本人")
    private Boolean selfResolved;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单类型：demand / fault")
    private String type;

    @Schema(description = "优先级：low / medium / high / urgent")
    private String priority;

    @Schema(description = "整理后的工单描述")
    private String problemDescription;

    @Schema(description = "用户原始描述，一行一轮")
    private String originalProblemDescription;
}
