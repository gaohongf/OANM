package com.github.gaohongf.wo.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import lombok.Data;

/**
 * 结单。
 *
 * <h2>心得为什么允许为空</h2>
 * 结单的<b>本体</b>是把状态推进到 {@code RESOLVED}；心得是让受理人记录处理过程的附加信息。
 * 把它做成必填，会让"处理完了但没什么可写"的人卡在一个和业务无关的输入框上。
 *
 * <h2>长度与列宽一致</h2>
 * 上限 1000 对应 {@code work_orders.assignee_note VARCHAR(1000)}。在这里拦下超长文本，
 * 而不是让它走到数据库上变成一个 500 —— 和建单接口对长文本的处理保持一致。
 */
@Data
public class ResolveWorkOrderCommand {

    /** 本次处理心得，选填 */
    @Length(max = 1000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String note;
}
