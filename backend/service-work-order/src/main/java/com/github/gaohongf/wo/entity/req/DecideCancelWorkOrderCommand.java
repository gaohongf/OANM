package com.github.gaohongf.wo.entity.req;

import lombok.Data;

/**
 * 处理提单人的取消申请。
 *
 * <h2>为什么用 Boolean 而不是 boolean</h2>
 * 基本类型遇到空请求体会默认成 {@code false}，也就是"没表态"被静默当成"拒绝"。
 * 用包装类型把"没给值"保留下来，由服务层明确拒绝这次请求。
 */
@Data
public class DecideCancelWorkOrderCommand {

    /** {@code true} 同意取消；{@code false} 拒绝并保留工单；{@code null} 视为参数错误 */
    private Boolean approved;
}
