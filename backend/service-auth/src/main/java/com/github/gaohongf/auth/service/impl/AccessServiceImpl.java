package com.github.gaohongf.auth.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.github.gaohongf.auth.dao.UserDao;
import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.entity.po.credentials.UsernameAndPassword;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.AccessService;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class AccessServiceImpl implements AccessService {

    private final PasswordEncoder passwordEncoder;
    private final UserDao userDao;

    @Override
    public SaTokenInfo login(UsernameAndPassword usernameAndPassword) {
        usernameAndPassword.check();
        String username = usernameAndPassword.getUsername();
        String password = usernameAndPassword.getPassword();
        UserEntity user = userDao.selectByUsername(username)
                .orElseThrow(() -> R.error(AuthRsm.USER_DOES_NOT_EXIST));

        if (passwordEncoder.matches(password, user.getPassword())) {
            StpUtil.login(user.getId());
            return StpUtil.getTokenInfo();
        } else {
            return R.error(AuthRsm.PASSWORD_ERROR);
        }
    }

}
