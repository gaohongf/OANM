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

        // 先验密码再查封禁状态: 不知道密码的人不该从错误信息里确认这个账号存在。
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return R.error(AuthRsm.PASSWORD_ERROR);
        }

        if (Boolean.TRUE.equals(user.getLocked())) {
            // 403, 前端会原样展示后端消息(401 会被当成登录过期并丢弃)
            return R.error(AuthRsm.ACCOUNT_LOCKED);
        }

        StpUtil.login(user.getId());
        return StpUtil.getTokenInfo();
    }

}
