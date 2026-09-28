package com.github.gaohongf.ai.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.prompt.PromptTemplate;

import com.github.gaohongf.ai.entity.po.PromptTemplateEntity;

public final class TemplateUtil {
    private TemplateUtil() {
    }

    public PromptTemplate setTemplate(PromptTemplateEntity promptTemplateEntity, Map<String, Object> map) {

        Map<String, Object> variablesMap = new HashMap<>(map);

        variablesMap.computeIfAbsent("now", (key) -> LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE")));
        
        return PromptTemplate.builder().template(
                promptTemplateEntity.getTemplate()).variables(variablesMap).build();
    }
}
