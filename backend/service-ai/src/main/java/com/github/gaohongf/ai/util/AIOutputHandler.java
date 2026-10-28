package com.github.gaohongf.ai.util;

import org.springframework.ai.converter.StructuredOutputConverter;

import reactor.core.publisher.Flux;

/**
 * AI 输出处理器
 */
public interface AIOutputHandler<T> {
    Flux<T> handle(Flux<String> output);
    StructuredOutputConverter<T> getConverter();
}
