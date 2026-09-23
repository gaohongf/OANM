package com.github.gaohongf.auth.controller;

import com.github.gaohongf.auth.service.AccessService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.entity.po.credentials.UsernameAndPassword;

import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/auth/acc")
public class AccessController {

    private final AccessService accessService;

    @IsOpen
    @PostMapping("/login")
    public SaTokenInfo login(@RequestBody UsernameAndPassword usernameAndPassword) {
        return accessService.login(usernameAndPassword);
    }
}
