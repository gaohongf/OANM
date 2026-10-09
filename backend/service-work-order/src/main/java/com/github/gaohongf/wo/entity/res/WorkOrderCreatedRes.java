package com.github.gaohongf.wo.entity.res;

/**
 * 建单结果。
 *
 * <h2>为什么是对象而不是裸 id</h2>
 * 两个原因，都不是风格问题：
 *
 * <ol>
 * <li><b>裸 {@code String} 出网会被 RSM 漏掉包装。</b> {@code JsonResponseBodyPacker.shouldPack}
 * 在转换器是 {@code StringHttpMessageConverter} 时直接返回 false —— 也就是返回裸字符串的接口
 * <b>不会</b>被包成 {@code {code,msg,data}}。前端拿到的结构与所有其他接口都不一样，
 * 而它的解包逻辑（{@code http.ts} 的 {@code request}）只看信封，会当成解析失败。
 * 框架为此提供了 {@code RString}（standard.md 也提到），但见下一条。</li>
 * <li><b>{@code data} 是标量，日后加字段就是破坏性变更。</b> 建单结果要带上 id
 * 是眼下的全部需求，但"建单还能返回什么"是可预期的（状态、创建时间…）。
 * 一个对象可以在不改客户端的前提下长大。</li>
 * </ol>
 *
 * <p>
 * 字段只有一个也不要紧 —— 它的价值在于给 {@code data} 定了一个可扩展的形状。
 *
 * @param id 新建工单的 id。<b>字符串形式</b>，理由见 {@link WorkOrderRes}：雪花 ID
 *           超出 JS 的精确整数范围，用数字出网会被悄悄改值。
 */
public record WorkOrderCreatedRes(String id) {
}
