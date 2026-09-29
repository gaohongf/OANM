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

import com.github.gaohongf.auth.entity.req.SaveMenuCommand;
import com.github.gaohongf.auth.entity.res.MenuRes;
import com.github.gaohongf.auth.service.MenuService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 菜单管理（管理端）。
 * <p>
 * 注意这里返回的是<b>不过滤</b>的完整树 —— 管理界面必须能看到自己无权访问的节点,
 * 否则没法为别人配置它们。当前用户可见的那棵树在 {@code /api/auth/me} 里。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth/menus")
public class MenuController {

    private final MenuService menuService;

    /** 全量菜单树（含隐藏与已停用节点）。量级是几十到几百, 不分页。 */
    @GetMapping
    public List<MenuRes> tree() {
        return menuService.tree();
    }

    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public void create(@RequestBody @Valid SaveMenuCommand command) {
        menuService.create(command);
    }

    /**
     * 修改菜单。改变 {@code parentId} 相当于移动节点, 服务层会校验不会成环
     * （把自己的父节点移到自己的子孙下面会让建树变成死循环）。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}")
    public void update(
            @PathVariable("id") Long id,
            @RequestBody @Valid SaveMenuCommand command) {
        menuService.update(id, command);
    }

    /**
     * 删除菜单。有子节点时会被拒绝, 不会静默级联删除整棵子树。
     */
    @ExecutionSuccess(GenericRsm.DELETE_SUCCESS)
    @ExecutionFailed(GenericRsm.DELETE_FAILED)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        menuService.delete(id);
    }
}
