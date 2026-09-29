package com.github.gaohongf.auth.service;

import java.util.List;

import com.github.gaohongf.auth.entity.req.SaveMenuCommand;
import com.github.gaohongf.auth.entity.res.MenuRes;

/**
 * 菜单管理。
 */
public interface MenuService {

    /**
     * 全部菜单（管理端用）: 含 hidden 与已停用的节点, 不做权限过滤。
     * <p>
     * 管理界面必须能看到自己无权访问的节点, 否则无法为别人配置它们。
     */
    List<MenuRes> tree();

    /**
     * 当前登录用户可见的菜单树（前端用）: 只含启用且已获授权的节点。
     */
    List<MenuRes> treeForCurrentUser();

    void create(SaveMenuCommand command);

    /** 修改节点; 若 parentId 变化相当于移动, 会校验不会成环。 */
    void update(Long id, SaveMenuCommand command);

    /** 删除节点。有子节点时拒绝, 不做静默级联。 */
    void delete(Long id);
}
