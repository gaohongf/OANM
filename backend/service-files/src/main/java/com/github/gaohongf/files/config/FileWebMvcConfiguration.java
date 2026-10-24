package com.github.gaohongf.files.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册上传相关拦截器。
 *
 * <p>
 * 只挂在图片接口上: 大文件接口本来就要收 1GB, 提前拒绝没有意义, 大小判断在
 * {@link com.github.gaohongf.files.service.impl.FileServiceImpl#uploadLarge} 里。
 */
@Configuration
public class FileWebMvcConfiguration implements WebMvcConfigurer {

    /** 鉴权拦截器（common 自动装配, 默认 order 0）先跑, 本拦截器排在它后面 */
    private static final int UPLOAD_SIZE_INTERCEPTOR_ORDER = 1;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new UploadSizeInterceptor())
                .addPathPatterns("/api/files/upload/image")
                .order(UPLOAD_SIZE_INTERCEPTOR_ORDER);
    }
}
