package com.github.gaohongf.ai.util;

import java.time.LocalDateTime;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class DateTimeTools {
    @Tool(description = "获取当前用户的日期和时间")
    public String getCurrentDateTime() {
        return LocalDateTime.now()
                .atZone(LocaleContextHolder.getTimeZone().toZoneId())
                .toString();
    }
}
