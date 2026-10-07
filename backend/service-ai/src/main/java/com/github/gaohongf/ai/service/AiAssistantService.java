package com.github.gaohongf.ai.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service 
public interface AiAssistantService {
    Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String userInput);
}
