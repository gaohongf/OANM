package com.github.gaohongf.auth.service;

import org.springframework.stereotype.Service;

import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.entity.req.GrantRolesCommand;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.auth.entity.res.UserAdminRes;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;

@Service
public interface UserService {
    void createUser(CreateUserCommand command);

    UserRes findUser(Long id);

    /**
     * 分页查询用户（含各自的角色），供管理端的用户列表与"分配角色"使用。
     *
     * @param keyword 按用户名或昵称模糊匹配，可为空
     */
    PageRes<UserAdminRes> page(long current, long size, String keyword);

    /**
     * 查一个用户的角色与权限。
     * <p>
     * 这是"角色 + 权限"唯一的组装点: 本服务的 {@code StpInterfaceImpl} 和对外提供的
     * 内部端点都走这里, 免得两处各拼一遍、日后加一种授权来源时漏改其中一处。
     */
    UserAuthorities findAuthorities(Long id);

    /**
     * 给用户授角色, <b>整体替换</b>（传空集合等于收回该用户的全部角色）。
     * <p>
     * 与"给角色授权"的区别在于影响面: 这个只影响一个人, 所以缓存只需失效他自己。
     */
    void grantRoles(Long userId, GrantRolesCommand command);
}
