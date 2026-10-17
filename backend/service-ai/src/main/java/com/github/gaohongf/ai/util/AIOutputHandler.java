package com.github.gaohongf.ai.util;

import reactor.core.publisher.Flux;

/**
 * AI 输出处理器
 */
public interface AIOutputHandler<T> {
    Flux<T> handle(Flux<String> output);
}
