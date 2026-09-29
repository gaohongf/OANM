package com.github.gaohongf.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.entity.req.GrantPermissionsCommand;
import com.github.gaohongf.auth.entity.req.SaveRoleCommand;
import com.github.gaohongf.auth.entity.res.PermissionRes;
import com.github.gaohongf.auth.entity.res.RoleRes;
import com.github.gaohongf.auth.service.RoleService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 角色管理。
 * <p>
 * 这些接口都需要权限（没有 {@code @IsOpen}），所以第一个管理员必须靠
 * {@code db/seed.sql} 授予的 {@code *} 超级权限进来 —— 见那个文件的说明。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth/roles")
public class RoleController {

    private final RoleService roleService;

    /** 全部角色。量级小, 不分页。 */
    @GetMapping
    public List<RoleRes> list() {
        return roleService.list();
    }

    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public void create(@RequestBody @Valid SaveRoleCommand command) {
        roleService.create(command);
    }

    /**
     * 修改角色。只会改 label —— {@code roleName} 是主键且被两张关联表以外键引用,
     * 不支持改名（命令对象里的 roleName 被忽略）。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{roleName}")
    public void update(
            @PathVariable("roleName") String roleName,
            @RequestBody @Valid SaveRoleCommand command) {
        roleService.update(roleName, command);
    }

    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    @DeleteMapping("/{roleName}")
    public void delete(@PathVariable("roleName") String roleName) {
        roleService.delete(roleName);
    }

    /**
     * 角色当前已授予的权限（完整对象）。
     * <p>
     * 授权是整体替换语义，界面需要拿这份完整集合作为"已选中"的初始值，
     * 再把完整集合提交回来 —— 否则只提交自己看到的那一页会误删其他页的授权。
     */
    @GetMapping("/{roleName}/permissions")
    public List<PermissionRes> permissions(@PathVariable("roleName") String roleName) {
        return roleService.findPermissions(roleName);
    }

    /**
     * 给角色授权, <b>整体替换</b>: 请求体里是这个角色最终应该拥有的权限全集。
     * 传空集合等于清空该角色的全部权限（不是"不改动"）。
     * <p>
     * 内置的超管权限 {@code *} 只有调用者自己持有它时才能授出去。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{roleName}/permissions")
    public void grantPermissions(
            @PathVariable("roleName") String roleName,
            @RequestBody GrantPermissionsCommand command) {
        roleService.grantPermissions(roleName, command);
    }
}
