package com.github.gaohongf.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.github.gaohongf.auth.AuthServiceApplication;
import com.github.gaohongf.auth.service.UserService;

import jakarta.annotation.Resource;

// @SpringBootTest(value = "Auth Test",classes = AuthServiceApplication.class)
public class AuthTest {

    @Resource
    private UserService userService;

    @Test
    void testCreateUser() {
        System.out.println("aaaaaaaaaaaaaaaaaaa");
    }
}
