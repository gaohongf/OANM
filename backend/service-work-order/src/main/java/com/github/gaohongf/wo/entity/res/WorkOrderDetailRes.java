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

    /** 用户在意图推断里选中的选项 id；手工建单和自助解决记录为 null */
    private Integer aiSelectedOptionId;

    /**
     * 受理人结单时填写的本次心得，没有填时为 {@code null}。
     * <p>
     * 受理人 id 在父类 {@link WorkOrderRes#getAssigneeId()} 上（带 {@code @User}）。
     * 这里刻意不再声明 {@code assigneeId}: 子类同名属性会遮蔽父类字段, MapStruct 会按
     * {@code Long -> String} 映射, 于是 {@code @User} 的解析被绕过, 详情和列表返回两种形状。
     */
    private String assigneeNote;
}
