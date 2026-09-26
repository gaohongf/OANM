package com.github.gaohongf.auth.service.impl;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.cache.CacheKeyConstants;
import com.github.gaohongf.auth.dao.UserDao;
import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.mapstruct.UserConverter;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.UserService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@CacheConfig(cacheNames = CacheKeyConstants.USER_TTL + CacheKeyConstants.USER_CACHE_TTL_SECONDS)
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final UserConverter userConverter;

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

    /**
     * {@code unless = "#result == null"} 不能省。
     * <p>
     * 缓存管理器（{@code CacheConfiguration}）开了 {@code disableCachingNullValues()},
     * 而"查无此人"时本方法就是返回 null —— 不加这个条件, 缓存切面会拿 null 去写缓存,
     * 直接抛 {@code IllegalArgumentException: Cache 'user' is configured to not allow
     * null values but null was provided}, 表现为按不存在的 id 查询时 500。
     * <p>
     * 这里刻意不在 L2 做负缓存: Redis 那份 TTL 是 12 小时, 把一个"此刻不存在"的用户
     * 钉在那里 12 小时, 意味着新建的用户半天看不见。负缓存交给调用方的 L1（TTL 60 秒）。
     */
    @Override
    @Cacheable(key = "#id", unless = "#result == null")
    public UserRes findUser(Long id) {
        UserEntity user = userDao.selectById(id);
        return userConverter.toRes(user);
    }
}
