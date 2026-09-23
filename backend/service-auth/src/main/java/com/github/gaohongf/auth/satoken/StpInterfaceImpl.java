package com.github.gaohongf.auth.satoken;

import java.util.List;

import org.springframework.stereotype.Component;

import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.dao.UserDao;

import cn.dev33.satoken.stp.StpInterface;
import lombok.AllArgsConstructor;

@Component 
@AllArgsConstructor 
public class StpInterfaceImpl implements StpInterface {

    private final UserDao userDao;

    private final PermissionDao permissionDao;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (loginId == null) {
            return List.of();
        }
        return permissionDao.selectPermissionKeysByUserId(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return List.of();
        }
        return userDao.selectRoleNamesByUserId(Long.valueOf(loginId.toString()));
    }
    
}
