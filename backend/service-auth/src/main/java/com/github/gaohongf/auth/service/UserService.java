package com.github.gaohongf.auth.service;

import org.springframework.stereotype.Service;

import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.res.UserRes;

@Service 
public interface UserService {
    void createUser(CreateUserCommand command);

    UserRes findUser(Long id);
}
