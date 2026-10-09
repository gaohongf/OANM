package com.github.gaohongf.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
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

    
    /**
     * 预测用户输入的意图
     * @param id
     * @param userInput
     * @return
     */
    @GetMapping(value = "/wo/uiii", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "userInput", required = true) String userInput) {
        return aiAssistantService.workOrderUserInputIntentInference(id, userInput);
    }
    /**
     * 协助用户提交工单
     * @param id
     * @param userSelect
     * @return
     */
    @GetMapping(value = "/wo/assist-submit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> workOrderSubmit(
            @RequestParam(value = "id", required = true) String id,
            @RequestParam(value = "userSelect", required = true) Integer userSelect) {
        return aiAssistantService.workOrderSubmit(id, userSelect);
    }
}
