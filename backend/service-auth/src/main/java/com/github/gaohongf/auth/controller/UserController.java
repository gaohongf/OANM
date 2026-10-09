package com.github.gaohongf.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.entity.req.GrantRolesCommand;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.auth.entity.res.UserAdminRes;
import com.github.gaohongf.auth.service.UserService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;
import com.lingyun.base.rsm.str.RString;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/api/auth/users")
public class UserController {
    private final UserService userService;
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public void createUser(
        @RequestBody 
        @Valid 
        CreateUserCommand command){
        userService.createUser(command);
    }

    /**
     * 分页查询用户（含各自的角色）。
     * <p>
     * 一次把角色带出来，是为了让"分配角色"的弹窗不必再发一个请求 ——
     * 否则列表显示的角色和弹窗里的初始选中值可能来自两个时刻，互相矛盾。
     *
     * @param keyword 按用户名或昵称模糊匹配
     */
    @GetMapping
    public PageRes<UserAdminRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return userService.page(current, size, keyword);
    }

    @GetMapping("/test")
    public RString test(){
        return RString.warp("hello!");
    }

    /**
     * 给用户授角色, <b>整体替换</b>: 请求体里是这个用户最终应该拥有的角色全集。
     * 传空集合等于收回该用户的全部角色（不是"不改动"）。
     * <p>
     * 只影响这一个人, 所以缓存只失效他自己 —— 与"给角色授权"（影响所有持有该角色的人）
     * 相比影响面小得多。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/roles")
    public void grantRoles(
            @PathVariable("id") Long id,
            @RequestBody GrantRolesCommand command) {
        userService.grantRoles(id, command);
    }
}
