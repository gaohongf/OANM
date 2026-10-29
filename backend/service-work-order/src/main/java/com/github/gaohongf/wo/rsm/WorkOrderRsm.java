package com.github.gaohongf.wo.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 工单相关的统一响应消息。
 *
 * <h2>不要给它加 @Component</h2>
 * 它由 {@code com.github.gaohongf.wo.config.WorkOrderRsmConfiguration} 注册。
 * <p>
 * {@code AuthRsm} 那边踩过一次这个坑，值得在这里重述，因为症状和原因都反直觉：
 * 一个没注册过的消息键，RSM 解析不出模板就退回默认消息，于是"工单不存在"会被渲染成
 * {@code {"code":1016,"msg":"成功"}} —— <b>拒绝被报成了成功</b>，而且不抛错、不告警。
 */
public class WorkOrderRsm implements RsmManager {

    /** 按 id 查工单但不存在（已删除或从未创建） */
    @RsmInfo(template = "工单不存在", status = HttpStatus.NOT_FOUND)
    public static final String WORK_ORDER_NOT_FOUND = "WorkOrder_WORK_ORDER_NOT_FOUND";

    /**
     * 工单类型不是 demand / fault。
     * <p>
     * 文案里把合法值列出来，是因为这个错误最常见的来源是模型输出的漂移 ——
     * 调用方看到"只能是 demand 或 fault"就知道该往哪儿改，而"参数非法"提供不了这个信息。
     */
    @RsmInfo(template = "工单类型只能是 demand 或 fault", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_TYPE_INVALID = "WorkOrder_WORK_ORDER_TYPE_INVALID";

    /** 优先级不是 low / medium / high / urgent */
    @RsmInfo(template = "工单优先级只能是 low、medium、high 或 urgent", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_PRIORITY_INVALID = "WorkOrder_WORK_ORDER_PRIORITY_INVALID";

    /**
     * 现场图片地址不合法。
     * <p>
     * 只接受两种：本项目上传接口返回的相对地址（{@code /file/...}），以及明确以
     * {@code http://} / {@code https://} 开头的链接。这个字段最终会渲染成 {@code <img src>}，
     * 放行 {@code javascript:} / {@code data:} 之类的前缀没有正当用途。
     */
    @RsmInfo(template = "图片地址不合法，只支持上传得到的相对地址或 http(s) 链接", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_IMAGE_URL_INVALID = "WorkOrder_WORK_ORDER_IMAGE_URL_INVALID";

    /**
     * 列表筛选传了不认识的状态。
     * <p>
     * 这里拒绝而不是忽略，是因为"忽略"的表现是<b>返回全部工单</b> —— 界面上看起来
     * 筛选生效了，实际上没有。这种"看起来对"的错误比一个 400 难查得多。
     */
    @RsmInfo(template = "工单状态只能是 NEW、ACCEPTED、RESOLVED 或 CLOSED", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_STATUS_INVALID = "WorkOrder_WORK_ORDER_STATUS_INVALID";

    /**
     * 列表范围传了不认识的值。
     * <p>
     * 与状态筛选同理：忽略非法值会返回全部工单，界面看起来筛选生效了，实际没有。
     */
    @RsmInfo(template = "工单范围只能是 all、created 或 assigned", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_SCOPE_INVALID = "WorkOrder_WORK_ORDER_SCOPE_INVALID";

    /**
     * 工单当前状态不允许接取。
     * <p>
     * 只有"待受理且尚未派发"的工单能被接取。文案把这个条件写出来，是因为最常见的误用是
     * 对一个已经属于别人的工单再点一次接取 —— 看到条件就知道该刷新，而不是继续重试。
     */
    @RsmInfo(template = "工单当前状态不可接取（仅待受理且未派发的工单可以接取）", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_NOT_CLAIMABLE = "WorkOrder_WORK_ORDER_NOT_CLAIMABLE";

    /**
     * 并发接取失败：读到的还是待受理，更新时已经被人抢走了。
     * <p>
     * 用 409 而不是 400，因为请求本身合法，只是此刻的状态冲突了。
     */
    @RsmInfo(template = "工单已被他人接取，请刷新后重试", status = HttpStatus.CONFLICT)
    public static final String WORK_ORDER_ALREADY_CLAIMED = "WorkOrder_WORK_ORDER_ALREADY_CLAIMED";

    /** 工单当前状态不允许派发（只有待受理 / 已受理可派发） */
    @RsmInfo(template = "工单当前状态不可派发（仅待受理或已受理的工单可以派发）", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_NOT_ASSIGNABLE = "WorkOrder_WORK_ORDER_NOT_ASSIGNABLE";

    /**
     * 工单当前状态不允许结单。
     * <p>
     * 只有已受理的工单可以结单。待受理的单子还没人处理，已解决/已关闭的单子已经是终态。
     */
    @RsmInfo(template = "工单当前状态不可结单（仅已受理的工单可以结单）", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_NOT_RESOLVABLE = "WorkOrder_WORK_ORDER_NOT_RESOLVABLE";

    /**
     * 当前登录人不是这张工单的受理人。
     * <p>
     * 用 403 而不是 400：请求本身没问题，是操作者没有这个身份。
     */
    @RsmInfo(template = "只有受理人才能结单", status = HttpStatus.FORBIDDEN)
    public static final String WORK_ORDER_RESOLVE_FORBIDDEN = "WorkOrder_WORK_ORDER_RESOLVE_FORBIDDEN";

    /**
     * 工单上还有待受理人确认的取消申请。
     * <p>
     * 取消申请和结单是两条互斥的出路：先处理取消申请，避免出现"受理人刚结单、
     * 提单人却以为已经取消了"的状态。
     */
    @RsmInfo(template = "工单有待确认的取消申请，请先处理取消申请", status = HttpStatus.CONFLICT)
    public static final String WORK_ORDER_CANCEL_REQUEST_PENDING =
            "WorkOrder_WORK_ORDER_CANCEL_REQUEST_PENDING";

    /** 只有提单人本人可以取消自己提交的工单 */
    @RsmInfo(template = "只有提单人才能取消工单", status = HttpStatus.FORBIDDEN)
    public static final String WORK_ORDER_CANCEL_FORBIDDEN = "WorkOrder_WORK_ORDER_CANCEL_FORBIDDEN";

    /**
     * 工单当前状态不允许取消。
     * <p>
     * 待受理和已受理可以取消；已解决/已关闭是终态，不再支持取消。
     */
    @RsmInfo(template = "工单当前状态不可取消（仅待受理或已受理的工单可以取消）", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_NOT_CANCELABLE = "WorkOrder_WORK_ORDER_NOT_CANCELABLE";

    /** 要处理的取消申请不存在（没申请过，或者已经被处理掉了） */
    @RsmInfo(template = "工单没有待确认的取消申请", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_CANCEL_REQUEST_ABSENT =
            "WorkOrder_WORK_ORDER_CANCEL_REQUEST_ABSENT";

    /** 只有当前受理人可以处理取消申请 */
    @RsmInfo(template = "只有当前受理人可以处理取消申请", status = HttpStatus.FORBIDDEN)
    public static final String WORK_ORDER_CANCEL_DECISION_FORBIDDEN =
            "WorkOrder_WORK_ORDER_CANCEL_DECISION_FORBIDDEN";

    /** 处理取消申请时没有明确给出同意或拒绝 */
    @RsmInfo(template = "请明确选择同意或拒绝这次取消申请", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_CANCEL_DECISION_INVALID =
            "WorkOrder_WORK_ORDER_CANCEL_DECISION_INVALID";

    /**
     * 派发时版本冲突：校验通过后的那一刻，工单被另一个人改了。
     * <p>
     * 不做自动重试：受理人权限是远程校验的，重试时它可能已经变了，静默重放会掩盖掉这个问题。
     */
    @RsmInfo(template = "工单已被他人修改，请刷新后重试", status = HttpStatus.CONFLICT)
    public static final String WORK_ORDER_CONCURRENT_MODIFIED = "WorkOrder_WORK_ORDER_CONCURRENT_MODIFIED";

    /** 派发入参里的受理人 id 不是合法数字 */
    @RsmInfo(template = "受理人 id 格式不正确", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_ASSIGNEE_INVALID = "WorkOrder_WORK_ORDER_ASSIGNEE_INVALID";

    /** 要派给的人不存在（已删除或从未存在） */
    @RsmInfo(template = "指定的受理人不存在", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_ASSIGNEE_NOT_FOUND = "WorkOrder_WORK_ORDER_ASSIGNEE_NOT_FOUND";

    /** 要派给的人已被封禁 */
    @RsmInfo(template = "指定的受理人已被封禁", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_ASSIGNEE_LOCKED = "WorkOrder_WORK_ORDER_ASSIGNEE_LOCKED";

    /**
     * 要派给的人不持有接单权限。
     * <p>
     * 派发候选人就是"能自己接单的人"，否则派过去的单子他自己也处理不了。
     */
    @RsmInfo(template = "指定的受理人没有接单权限", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_ASSIGNEE_HAS_NO_CLAIM_PERMISSION =
            "WorkOrder_WORK_ORDER_ASSIGNEE_HAS_NO_CLAIM_PERMISSION";

    /**
     * 校验受理人权限时 service-auth 不可用。
     * <p>
     * 刻意与"没有权限"分开：前者是暂时的依赖故障，重试有意义；后者是授权问题，重试没有意义。
     * 用 503 表示"现在做不了，稍后再来"。
     */
    @RsmInfo(template = "暂时无法校验受理人权限，请稍后重试", status = HttpStatus.SERVICE_UNAVAILABLE)
    public static final String WORK_ORDER_ASSIGNEE_VERIFY_UNAVAILABLE =
            "WorkOrder_WORK_ORDER_ASSIGNEE_VERIFY_UNAVAILABLE";
}
