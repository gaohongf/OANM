package com.github.gaohongf.files.constant;

/**
 * 两个上传接口各自的大小上限。
 *
 * <p>单独成类是因为有两处要用: service 层校验解析后的真实文件大小,
 * 拦截器在解析请求体之前按 Content-Length 粗筛。两处必须是同一组数字,
 * 否则会出现"拦截器放过、service 又拒绝"这种看起来自相矛盾的行为。
 *
 * <p>前端 {@code web/oanm-web/src/api/files.ts} 里有同样的常量, 只用于上传前提示;
 * 改这里时记得同步, 不然用户要等传完才看到"文件太大"。
 */
public final class UploadLimits {

    /** 图片上传接口: 单文件 10MB */
    public static final long IMAGE_MAX_BYTES = 10L * 1024 * 1024;

    /** 大文件上传接口: 单文件 1GB */
    public static final long LARGE_MAX_BYTES = 1024L * 1024 * 1024;

    /**
     * multipart 边界、part 头和其它表单字段的余量。
     * <p>
     * Content-Length 是整个请求体的大小, 不是文件本身的大小; 直接拿它和
     * {@link #IMAGE_MAX_BYTES} 比较, 会把刚好 10MB 的图片误判成超限。
     */
    public static final long MULTIPART_OVERHEAD_BYTES = 1024L * 1024;

    private UploadLimits() {
    }
}
