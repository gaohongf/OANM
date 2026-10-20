package com.github.gaohongf.files.entity.res;

/**
 * 上传结果。
 *
 * <h2>为什么带 id 和 filename</h2>
 * {@code url} 是访问地址, 也是后续删除的钥匙; 但记录 id 和存储文件名在排查
 * "文件到底落在磁盘哪里"时有用 —— 它们来自 {@code file_detail}, 而不是猜出来的。
 *
 * <p>
 * 返回对象而不是裸 url 字符串: RSM 的 {@code StringHttpMessageConverter} 不会给裸字符串
 * 套信封, 前端拿到的结构会和其它接口不一致。
 *
 * @param url              访问地址。这里是相对地址（如 {@code /file/2026/10/19/xxx.jpg}）,
 *                         由浏览器按网关同源解析
 * @param originalFilename 用户上传时的原始文件名
 */
public record FileUploadRes(
        String id,
        String url,
        String filename,
        String originalFilename,
        Long size,
        String contentType) {
}
