package com.github.gaohongf.auth.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

@RestController 
@RequestMapping("/api/auth/users")
public class UserService {
    
    @ExecutionSuccess(value = GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed (value = GenericRsm.QUERY_FAILED)
    @GetMapping("/test")
    public Map<String, String> test(){
        return Map.of("name", "张三");
    }
}

