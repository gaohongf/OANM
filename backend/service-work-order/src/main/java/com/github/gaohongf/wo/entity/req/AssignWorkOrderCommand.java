package com.github.gaohongf.wo.entity.req;

import java.util.Optional;

import com.github.gaohongf.wo.rsm.WorkOrderRsm;
import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 派发工单：把某张工单指定给一个受理人。
 *
 * <h2>assigneeId 为什么收 String 而不是 Long</h2>
 * 用户 id 是雪花 id（19 位），前端如果把它当 {@code Number} 传过来，值在到达后端之前
 * 就已经被取整成另一个人了。收字符串既保证原样传输，也让"格式不对"变成一个能读懂的
 * 业务错误（{@link WorkOrderRsm#WORK_ORDER_ASSIGNEE_INVALID}），而不是报文解析失败。
 */
@Data
public class AssignWorkOrderCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    private String assigneeId;

    /**
     * 解析受理人 id。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 不是合法数字时
     */
    public Long toAssigneeId() {
        Optional<Long> parsed;
        try {
            parsed = Optional.of(Long.valueOf(assigneeId.trim()));
        } catch (NumberFormatException e) {
            parsed = Optional.empty();
        }
        if (parsed.isEmpty()) {
            // 独立语句：R.error 是 <T> T，塞进 orElseThrow 会被推成 Throwable 而要求声明 throws
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_INVALID);
        }
        return parsed.get();
    }
}
