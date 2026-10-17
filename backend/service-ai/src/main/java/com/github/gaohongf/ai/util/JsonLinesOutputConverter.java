package com.github.gaohongf.ai.util;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
/**
 * 提示AI要以 JSONL 格式输出
 * @param <T>
 */

public class JsonLinesOutputConverter<T> extends BeanOutputConverter<T> {

    public JsonLinesOutputConverter(Class<T> clazz) {
        super(clazz);
    }

    public JsonLinesOutputConverter(ParameterizedTypeReference<T> typeRef) {
        super(typeRef);
    }

    @Override
    public String getFormat() {
        // 1. 获取父类生成的、针对单个对象的 JSON Schema
        String schema = this.getJsonSchema();

        // 2. 构造 JSONL 专用的指令模板
        String template = """
            Your response must be in JSON Lines (JSONL) format.
            Each line must be a single, valid JSON object that strictly adheres to the JSON Schema below.
            Do not include any explanations, markdown code blocks, or extra text.
            Output one JSON object per line.

            JSON Schema for each line:
            ```%s```
            """;
        return String.format(template, schema);
    }
}
