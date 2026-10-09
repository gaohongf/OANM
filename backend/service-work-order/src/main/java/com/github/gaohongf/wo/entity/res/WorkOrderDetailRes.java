package com.github.gaohongf.wo.entity.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单详情：列表行的全部字段，加上打开抽屉才需要的正文与归档信息。
 *
 * <p>
 * 继承 {@link WorkOrderRes} 而不是重写一遍那几个字段 —— 两者是"同一实体的不同投影"，
 * 重复声明会让"列表里有的详情里没有"这种不一致变成可能，而且不会有任何报错。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WorkOrderDetailRes extends WorkOrderRes {

    /** AI 整理后的工单描述 */
    private String problemDescription;

    /**
     * 用户原始描述，一行一轮。
     * <p>
     * 用户补充几次就有几行，前端按行拆开展示。
     */
    private String originalProblemDescription;

    /** AI 给出的解决方法 */
    private String solutionDetail;

    /** 用户在意图推断里选中的选项 id；手工建单时为 null */
    private Integer aiSelectedOptionId;

    private Long assigneeId;

    private String assigneeNote;
}
