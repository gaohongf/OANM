package com.github.gaohongf.auth.service;

import org.springframework.stereotype.Service;

import com.github.gaohongf.auth.entity.req.CreateUserCommand;

@Service 
public interface UserService {
    void createUser(CreateUserCommand command);
}
