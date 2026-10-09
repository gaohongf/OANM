package com.github.gaohongf.wo.entity.req;

import java.util.Optional;

import org.hibernate.validator.constraints.Length;

import com.github.gaohongf.wo.entity.po.WorkOrderPriority;
import com.github.gaohongf.wo.entity.po.WorkOrderType;
import com.github.gaohongf.wo.rsm.WorkOrderRsm;
import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 建单。
 *
 * <h2>没有 check()</h2>
 * 按本项目的约定，{@code check()} 只放 bean validation 注解<b>表达不了</b>的跨字段规则
 * （{@code SaveRoleCommand} / {@code SavePermissionCommand} 同样刻意没有）。这里没有跨字段
 * 规则，值的合法域检查由 {@link #toType()} / {@link #toPriority()} 各自负责 —— 它们在
 * 解析失败时直接抛业务错误，不需要一个额外的"先校验、再解析"的两步舞。
 *
 * <h2>type / priority 为什么收 String 而不是枚举</h2>
 * 直接让 Jackson 绑成枚举时，非法值会变成"报文解析失败"，调用方拿到的是和业务无关的框架错误。
 * 收字符串再宽松解析，就能给出"只能是 demand 或 fault"这种能被直接理解的提示。
 *
 * <h2>为什么没有 status 字段</h2>
 * 新建的工单一律是 {@code NEW}，由服务端固定。让调用方在建单时自报"已解决"没有任何意义，
 * 只是给状态机开了个后门。
 */
@Data
public class SubmitWorkOrderCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 200, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String title;

    /** 工单类型，取值 demand / fault（也接受中文同义词，见 {@link WorkOrderType#parse}） */
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    private String type;

    /** 优先级，取值 low / medium / high / urgent（也接受中文同义词） */
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    private String priority;

    /**
     * AI 整理后的工单描述。
     * <p>
     * 没有按提示词里"80-200 字"那样加最小长度限制：那是给模型的写作要求，
     * 而这里是用户<b>可以编辑</b>之后的最终值。用户在表单上把描述改短是正当的编辑行为，
     * 用模型的输出习惯去拒绝它只会让人交不上工单。
     */
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 2000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String problemDescription;

    /**
     * 用户原始描述，一行一轮（用户补充几次就有几行）。
     * <p>
     * 允许为空：手工建单时用户可能只填了整理后的描述。
     */
    @Length(max = 16000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String originalProblemDescription;

    /** AI 给出的解决方法 */
    @Length(max = 16000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String solutionDetail;

    /**
     * AI 会话 ID，归档用。
     * <p>
     * 长度上限卡在 36 是因为它指向 {@code spring_ai_chat_memory.conversation_id}（varchar(36)），
     * 超长的值在那边查不到任何东西 —— 与其存一个悬空的指针，不如在这里就拒掉。
     */
    @Length(max = 36, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String aiConversationId;

    /** 用户在意图推断里选中的选项 id */
    private Integer aiSelectedOptionId;

    /**
     * 解析工单类型。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 取值不在合法域内时
     */
    public WorkOrderType toType() {
        Optional<WorkOrderType> parsed = WorkOrderType.parse(type);
        if (parsed.isEmpty()) {
            // 写成独立语句而不是 orElseThrow(() -> R.error(...)): R.error 是
            // <T> T error(...), 在 orElseThrow 的位置上类型变量会被推成 Throwable,
            // 编译器随即要求方法声明 throws —— 而它实际抛的是 unchecked 的 RequestException,
            // 于是要补一个假的类型见证才编得过。独立语句没有这个问题。
            R.error(WorkOrderRsm.WORK_ORDER_TYPE_INVALID);
        }
        return parsed.get();
    }

    /**
     * 解析优先级。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 取值不在合法域内时
     */
    public WorkOrderPriority toPriority() {
        Optional<WorkOrderPriority> parsed = WorkOrderPriority.parse(priority);
        if (parsed.isEmpty()) {
            R.error(WorkOrderRsm.WORK_ORDER_PRIORITY_INVALID);
        }
        return parsed.get();
    }
}
