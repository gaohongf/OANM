package com.github.gaohongf.ai.util;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class JsonLinesAIOutputHandler<T> implements AIOutputHandler<T> {

    private final JsonLinesOutputConverter<T> converter;

    public JsonLinesAIOutputHandler(Class<T> clazz) {
        this.converter = new JsonLinesOutputConverter<>(clazz);
    }

    public JsonLinesAIOutputHandler(JsonLinesOutputConverter<T> converter) {
        this.converter = converter;
    }

    @Override
    public Flux<T> handle(Flux<String> output) {
        StringBuilder buffer = new StringBuilder();
        return Flux.<String>create(sink -> {
            Disposable disposable = output.subscribe((token) -> {
                int idx = -1;
                buffer.append(token);
                while ((idx = buffer.indexOf("\n")) != -1) {
                    sink.next(buffer.substring(0, idx + 1));
                    buffer.replace(0, idx + 1, "");
                }
            }, sink::error, () -> {
                if (!buffer.isEmpty()) {
                    sink.next(buffer.toString());
                }
                sink.complete();
            });
            sink.onDispose(disposable);
        }).map(converter::convert);
    }
}
