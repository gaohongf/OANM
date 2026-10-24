package com.github.gaohongf.files.config;

import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import com.github.gaohongf.files.constant.UploadLimits;
import com.github.gaohongf.files.rsm.FileRsm;
import com.lingyun.base.rsm.R;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 图片上传接口的"请求体太大"提前拒绝。
 *
 * <h2>为什么需要它</h2>
 * multipart 的 servlet 级上限为了大文件接口放宽到了 1GB（见 application.yml）, 而图片
 * 接口只有 10MB。只靠 service 层校验的话, 一个 900MB 的"图片"会先被完整写进临时目录,
 * 再被业务判断拒掉 —— 白占一次磁盘 IO, 还给了任何登录用户一个填满临时目录的办法。
 *
 * <h2>为什么按 Content-Length 而不是文件大小</h2>
 * 这一步发生在请求体解析之前, 拿不到"这个 part 有多大", 只能看整个请求体的大小。因此
 * 阈值加上了 {@link UploadLimits#MULTIPART_OVERHEAD_BYTES} 的余量: 刚好 10MB 的文件
 * 加上边界和 part 头会略超 10MB, 不能因此误伤。
 * <p>
 * 客户端不发 Content-Length（chunked 上传）时这里拿不到大小, 直接放行, 由 service
 * 层的精确校验兜底。这里只做粗筛, 不是权限或正确性的判断。
 *
 * <h2>依赖延迟解析</h2>
 * 必须配合 {@code spring.servlet.multipart.resolve-lazily: true}。否则请求一进
 * DispatcherServlet 就被解析并落盘, 本拦截器执行时已经太晚了。
 */
public class UploadSizeInterceptor implements HandlerInterceptor {

    /** 允许的最大请求体: 图片上限 + multipart 结构余量 */
    private static final long MAX_IMAGE_REQUEST_BYTES =
            UploadLimits.IMAGE_MAX_BYTES + UploadLimits.MULTIPART_OVERHEAD_BYTES;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        // 未知长度（-1）时跳过: 交给 FileServiceImpl 的精确校验
        if (request.getContentLengthLong() > MAX_IMAGE_REQUEST_BYTES) {
            // R.error 会抛 RequestException, 由 RSM 统一渲染成错误响应
            return R.error(FileRsm.FILE_IMAGE_TOO_LARGE);
        }
        return true;
    }
}
