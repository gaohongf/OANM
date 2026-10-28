package com.github.gaohongf.ai.util;

import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.lang.Nullable;

import reactor.core.publisher.Flux;

public class SimpleAIOutputHandler implements AIOutputHandler<String> {
    @Override
    public Flux<String> handle(Flux<String> output) {
        return output;
    }

    @Override
    public StructuredOutputConverter<String> getConverter() {
        return new StructuredOutputConverter<String>() {

            @Override
            @Nullable
            public String convert(String str) {
                return str;
            }

            @Override
            public String getFormat() {
                return "";
            }
            
        };
    }
}
