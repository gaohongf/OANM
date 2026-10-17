package com.github.gaohongf.ai.util;

import reactor.core.publisher.Flux;

public class SimpleAIOutputHandler implements AIOutputHandler<String> {
    @Override
    public Flux<String> handle(Flux<String> output) {
        return output;
    }
}
