package com.github.gaohongf.ai.client;

/**
 * 工单服务建单接口返回的数据。
 *
 * <p>
 * {@code id} 是雪花 id，工单服务已经把它序列化成字符串了（出网到 JS 不能是 Long），
 * 这里保持 String，原样交给前端。
 */
public record WorkOrderCreated(String id) {
}
