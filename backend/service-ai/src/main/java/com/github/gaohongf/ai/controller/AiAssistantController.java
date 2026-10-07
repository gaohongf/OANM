package com.github.gaohongf.ai.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.ai.service.AiAssistantService;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

@AllArgsConstructor
@RequestMapping("/api/ai/assistant")
@RestController
public class AiAssistantController {
    private final AiAssistantService aiAssistantService;

    @GetMapping(value = "/wouiii", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(
           @RequestParam(value = "userInput", required = true) String userInput) {
        return aiAssistantService.workOrderUserInputIntentInference(userInput);
    }
}
