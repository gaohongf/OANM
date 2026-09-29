package com.github.gaohongf.auth.service;

import java.util.List;

import com.github.gaohongf.auth.entity.req.GrantPermissionsCommand;
import com.github.gaohongf.auth.entity.req.SaveRoleCommand;
import com.github.gaohongf.auth.entity.res.PermissionRes;
import com.github.gaohongf.auth.entity.res.RoleRes;

/**
 * 角色管理。
 */
public interface RoleService {

    /** 全部角色。量级很小(通常个位数到几十), 不分页。 */
    List<RoleRes> list();

    /**
     * 角色当前已授予的权限（完整对象，不只是 id）。
     * <p>
     * 授权接口是<b>整体替换</b>语义，所以界面必须拿着"完整的已授权集合"去提交 ——
     * 只拿 id 而权限列表是分页的，界面就得再查一遍才能显示已授权项的名字；
     * 更糟的是如果界面只提交自己看到的那部分，会把其他页的授权<b>静默删掉</b>。
     * 这里一次把完整的已授权集合（含名称）给出去，就是为了让界面能把全集原样提交回来。
     */
    List<PermissionRes> findPermissions(String roleName);

    void create(SaveRoleCommand command);

    /**
     * 修改角色。{@code roleName} 是主键、不可修改, 所以这里只改 {@code label};
     * 命令对象里的 {@code roleName} 被忽略。
     */
    void update(String roleName, SaveRoleCommand command);

    /** 删除角色。被用户持有时拒绝; 否则连同它的授权一起逻辑删除。 */
    void delete(String roleName);

    /**
     * 给角色授权, <b>整体替换</b>（传空集合等于清空该角色的权限）。
     * <p>
     * 内置的超管权限 {@code *} 只有已经持有它的人才能授出去, 否则就是一个提权路径。
     */
    void grantPermissions(String roleName, GrantPermissionsCommand command);
}
