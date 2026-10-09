package com.github.gaohongf.auth.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.entity.req.SavePermissionCommand;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.auth.entity.res.PermissionRes;
import com.github.gaohongf.auth.service.PermissionService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 权限管理。
 * <p>
 * 分页是必需的: 权限会随自注册增长到"一个端点一行"。角色和菜单通常是几十行, 不分页。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 分页查询。
     *
     * @param keyword 按权限标识或标签模糊匹配, 不传则返回全部
     */
    @GetMapping
    public PageRes<PermissionRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return permissionService.page(current, size, keyword);
    }

    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public void create(@RequestBody @Valid SavePermissionCommand command) {
        permissionService.create(command);
    }

    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}")
    public void update(
            @PathVariable("id") Long id,
            @RequestBody @Valid SavePermissionCommand command) {
        permissionService.update(id, command);
    }

    /**
     * 内置的超管权限（键为 {@code *}）不允许删除; 被菜单引用的权限也不允许删除
     * —— 见 {@code PermissionServiceImpl} 里的说明, 那是 fail-open 的防线。
     */
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        permissionService.delete(id);
    }
}
