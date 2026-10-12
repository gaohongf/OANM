package com.github.gaohongf.message.template;

/**
 * 渲染好的标题与正文。
 *
 * <h2>为什么要专门一个类型</h2>
 * 标题和正文是<b>同一次渲染的两个产物</b>: 要么都成功, 要么都不落库
 * （见 {@link TemplateRenderer} 的契约）。如果渠道发送方收的是两个 {@code String} 参数,
 * 调用方就很容易写成"标题渲染失败就传空串"这种把半条消息发出去的形式 ——
 * 参数位置上少一个东西, 编译器不会说话。包成一个类型之后, 构造它的地方只有一处。
 *
 * @param title   渲染后的标题
 * @param content 渲染后的正文
 */
public record RenderedContent(String title, String content) {
}
