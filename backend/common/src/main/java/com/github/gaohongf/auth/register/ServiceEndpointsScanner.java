package com.github.gaohongf.auth.register;

/**
 * 解耦
 * 避免解析路径依赖Servlet或Reactive
 * ServiceEndpointsScanner
 */
public interface ServiceEndpointsScanner {
    ServiceEndpoints scan();
}
