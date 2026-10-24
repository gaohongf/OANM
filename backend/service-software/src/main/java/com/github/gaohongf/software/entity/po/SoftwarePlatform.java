package com.github.gaohongf.software.entity.po;

import java.util.Locale;
import java.util.Optional;

/**
 * 软件版本适用的平台。
 *
 * <p>
 * {@link #COMMON} 是"通用"：筛选某个具体平台时也会带上它，不会因为标记成通用就搜不到。
 */
public enum SoftwarePlatform {

    WINDOWS,
    MACOS,
    LINUX,
    COMMON;

    /** 宽松解析：去空格、忽略大小写。非法值返回 empty，由调用方映射成业务错误 */
    public static Optional<SoftwarePlatform> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (SoftwarePlatform value : values()) {
            if (value.name().equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** 筛选语义：具体平台要同时匹配"通用"；通用只匹配通用 */
    public boolean matchesFilter(SoftwarePlatform filter) {
        if (filter == null) {
            return true;
        }
        if (filter == COMMON) {
            return this == COMMON;
        }
        return this == filter || this == COMMON;
    }
}
