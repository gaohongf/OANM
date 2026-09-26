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
        // R.error 的签名是 <T> T error(...) 且内部直接 throw, 返回类型靠目标类型推断。
        // 这里不写类型见证的话, orElseThrow 的 Supplier<? extends X> 会把 X 推成 Throwable,
        // 于是 login 被要求声明 throws Throwable。指定成 RuntimeException 才和实际抛出的
        // RequestException（unchecked）相符。
        UserEntity user = userDao.selectByUsername(username)
                .orElseThrow(() -> R.<RuntimeException>error(AuthRsm.USER_DOES_NOT_EXIST));

        if (passwordEncoder.matches(password, user.getPassword())) {
            StpUtil.login(user.getId());
            return StpUtil.getTokenInfo();
        } else {
            return R.error(AuthRsm.PASSWORD_ERROR);
        }
    }

}
