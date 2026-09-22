package com.github.gaohongf.auth.service;

import org.springframework.stereotype.Service;

import com.github.gaohongf.auth.entity.po.credentials.UsernameAndPassword;

import cn.dev33.satoken.stp.SaTokenInfo;

@Service
public interface AccessService {
    SaTokenInfo login(UsernameAndPassword usernameAndPassword);
}
