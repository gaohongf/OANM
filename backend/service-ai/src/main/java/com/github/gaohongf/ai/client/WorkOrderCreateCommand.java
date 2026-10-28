package com.github.gaohongf.ai.client;

import lombok.Data;

/**
 * 发给工单服务 {@code POST /api/ops/work_order} 的报文。
 *
 * <p>
 * 字段名与工单服务的 {@code SubmitWorkOrderCommand} 一一对应。这里不复用那个类，
 * 是因为它属于另一个服务的内部包：跨服务共享实体类会把两边的发布绑在一起。
 */
@Data
public class WorkOrderCreateCommand {

    private String title;
    private String type;
    private String priority;
    private String problemDescription;
    private String originalProblemDescription;
    private String aiConversationId;
    private Integer aiSelectedOptionId;
    /** true 时工单服务把记录直接置为已解决、受理人为建单人 */
    private Boolean selfResolved;
}
