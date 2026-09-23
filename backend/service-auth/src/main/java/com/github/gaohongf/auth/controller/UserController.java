package com.github.gaohongf.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.service.UserService;
import com.lingyun.base.rsm.str.RString;

import lombok.AllArgsConstructor;

@AllArgsConstructor 
@RestController
@RequestMapping("/auth/users")
public class UserController {
    private final UserService userService;
    
    @PostMapping
    public void createUser(@RequestBody CreateUserCommand command){
        userService.createUser(command);
    }

    @GetMapping("/test")
    public RString test(){
        return RString.warp("hello!");
    }
}
