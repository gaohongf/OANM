package com.github.gaohongf.wo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController 
@RequestMapping("/api/ops/work_order")
public class OpsWorkOrderController {
    
    @GetMapping("/{id}")
    public Map<String, Object> getMethodName(@PathVariable("id") String id) {
        return Map.of("id", id, "name", "测试");
    }
    
}
