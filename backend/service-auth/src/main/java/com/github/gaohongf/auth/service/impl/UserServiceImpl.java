package com.github.gaohongf.auth.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.UserDao;
import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.UserService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@AllArgsConstructor 
@Service 
public class UserServiceImpl implements UserService{

    private final UserDao userDao;

    private final PasswordEncoder passwordEncoder;

    @Override
    public void createUser(CreateUserCommand command) {
        command.check();
        if (userDao.exists(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, command.getUsername()))) {
            R.error(AuthRsm.ACCOUNT_EXISTS);
        }


        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(command.getUsername());
        String encodedPassword = passwordEncoder.encode(command.getPassword());
        userEntity.setPassword(encodedPassword);
        userDao.insert(userEntity);
    }
    
}
