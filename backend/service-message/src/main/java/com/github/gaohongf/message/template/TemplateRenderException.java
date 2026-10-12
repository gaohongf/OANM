package com.github.gaohongf.message.template;

/**
 * 模板渲染失败。见 {@link TemplateRenderer} 的契约: 渲染失败<b>不落库</b>。
 * <p>
 * 用异常而不是返回 null/空串: 调用方（{@code NotificationServiceImpl}）必须"要么拿到完整内容,
 * 要么什么都不存", 而返回值方案里这两件事长得很像 —— 一个 {@code null} 检查漏写,
 * 存进去的就是一条正文是 "null" 的消息。
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message) {
        super(message);
    }
}
