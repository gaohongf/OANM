package com.github.gaohongf.auth.service;

import com.github.gaohongf.auth.entity.req.SavePermissionCommand;
import com.github.gaohongf.auth.entity.res.PageRes;
import com.github.gaohongf.auth.entity.res.PermissionRes;

/**
 * 权限管理。
 */
public interface PermissionService {

    /**
     * 分页查询。
     * <p>
     * 权限必须分页: 它会随自注册增长到"一个端点一行", 几百上千行是常态, 而角色和菜单
     * 通常只有几十行, 所以只有这里分页。
     *
     * @param keyword 按 permissionKey / label 模糊匹配, 可为空
     */
    PageRes<PermissionRes> page(long current, long size, String keyword);

    void create(SavePermissionCommand command);

    void update(Long id, SavePermissionCommand command);

    /** 删除权限。内置的超管权限、以及被菜单引用的权限会被拒绝。 */
    void delete(Long id);
}
