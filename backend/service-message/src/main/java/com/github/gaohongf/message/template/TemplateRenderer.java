package com.github.gaohongf.message.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 把 {@code {占位符}} 换成参数值。
 *
 * <h2>为什么不用 {@code MessageFormat}</h2>
 * 它是 JDK 自带的, 但有两个知名的坑, 而且都会安静地产生错文案:
 * <ul>
 *   <li><b>缺参会静默留下 {@code {0}}</b> —— 用户收到"工单「{0}」已提交"。</li>
 *   <li><b>单引号是转义字符</b> —— 文案里写"请勿''删除"会变成"请勿删除",
 *       而写"请勿'删除"会因为引号不配对把后面的占位符一起吞掉。</li>
 * </ul>
 * 中文文案里出现撇号、书名号是常态, 这个坑迟早会踩到, 且极难从现象反推。
 *
 * <h2>为什么不用 {@code String.format}</h2>
 * 只支持位置参数（{@code %s}）—— 位置和顺序绑死, 模板里插一句就全错位;
 * 而且带上 locale 语义, 同一个模板在不同机器上可能产出不同的数字格式。
 *
 * <h2>契约: 缺参即抛, 不产出"半成品"文案</h2>
 * 模板里出现而参数里没有的名字 → {@link TemplateRenderException}, 于是这条消息
 * <b>根本不会落库</b>。宁可用户收不到这条通知（少一条站内信, 没有任何副作用）,
 * 也不要给他发一条写着 {@code null} 或者半个句子的 —— 后者会让人以为业务出了问题,
 * 而那是一条查不出原因的错误信息。
 * <p>
 * 反过来, 参数里有而模板没用到的名字是<b>允许</b>的: 同一个 {@code args} 可能喂给
 * 标题和正文两个模板, 各自只用其中一部分。这不是错误, 所以不报。
 */
@Component
public class TemplateRenderer {

    /**
     * 占位符形状: 半个大括号包住一个"像变量名"的串。
     * <p>
     * 名字限字母/数字/下划线, 是为了让"文案里本来就想写一对大括号"（如
     * {@code 参数 {a, b}}）不被打扰 —— 那种写法里有空格和逗号, 匹配不上。
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /**
     * 渲染一个模板。
     *
     * @param template 模板文本, 如 {@code 工单「{title}」已提交}
     * @param args     占位符的值; 值为 null 也算"没给"（见下）
     * @throws TemplateRenderException 模板为空、或某个占位符在 args 里没有非 null 的值
     */
    public String render(String template, Map<String, Object> args) {
        if (template == null || template.isBlank()) {
            throw new TemplateRenderException("模板为空");
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = args == null ? null : args.get(name);
            // 值为 null 与"没有这个键"同等对待。区分二者只会让调用方需要多写一个
            // containsKey 检查, 而两者的对策完全一样（渲染不出来就整条不发）。
            if (value == null) {
                throw new TemplateRenderException("模板里的占位符 {" + name + "} 没有对应的参数值");
            }
            // quoteReplacement 不能省: 参数值里带 $ 或 \ 时（用户输入的标题就可能带上）
            // appendReplacement 会把它当分组引用, 直接抛 IllegalArgumentException。
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
