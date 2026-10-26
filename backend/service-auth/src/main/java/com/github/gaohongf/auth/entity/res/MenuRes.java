package com.github.gaohongf.auth.entity.res;

import java.util.List;

import lombok.Data;

/**
 * 菜单节点（树）。
 *
 * <h2>为什么这个 DTO 不用 MapStruct 生成</h2>
 * 项目里 entity → res 一律用 MapStruct 的同名映射, 但这里两处都不符合:
 * <ul>
 *   <li>{@code children} 是树结构的递归字段, 不是实体的属性, 要按 parent_id 组装</li>
 *   <li>{@code permissionIds / permissionKeys} 来自 {@code menu_permissions} 的 join
 *       (迁移期还包含旧列兜底), 不是实体的属性</li>
 * </ul>
 * 硬套 MapStruct 需要写 {@code @Mapping(ignore/expression)}, 比直接手写还难读。
 * 所以这个 DTO 由 {@code MenuServiceImpl} 手工组装。
 *
 * <h2>为什么 id 都是 String 而不是 Long</h2>
 * 理由与 {@code WorkOrderRes} 上那段相同（雪花 id 超过 JS 的 {@code 2^53 - 1} 就无法精确表示）。
 * 前端把这些值原样放进"上级菜单"和"所需权限"选择框, 保存时又原样发回来 ——
 * 只要其中一个在路上被取整, 保存出来的层级或门禁就是错的。
 */
@Data
public class MenuRes {

    private String id;

    /** 父节点; {@code null} 表示顶级 */
    private String parentId;

    private String name;

    /** 前端路由 */
    private String path;

    /** 前端页面标识(相对 src/pages/ 的路径); 为空即目录 */
    private String component;

    private String icon;

    private Integer sort;

    /** 在侧边栏隐藏（但仍可路由） */
    private Boolean hidden;

    private Boolean keepAlive;

    /**
     * 门禁权限 id; 空数组表示登录即可见。
     * <p>
     * 按 id 升序, 与 {@link #permissionKeys} 同长度、同顺序。
     */
    private List<String> permissionIds;

    /**
     * 与 {@link #permissionIds} 一一对应的权限键。
     * <p>
     * 权限行查不出来(例如被人手工删掉)时对应位置为 {@code null} —— 这条关联不授权,
     * 但<b>不能把它过滤掉</b>: 两个数组一旦错位, 界面就会把 A 的键显示在 B 的 id 上。
     * 一并返回也让前端不必再查一次权限表。
     */
    private List<String> permissionKeys;

    private Boolean enabled;

    /** 人工锁定; 前端的编辑界面据此判断是否可自由修改 */
    private Boolean locked;

    /** MANUAL / REGISTERED */
    private String source;

    /** 子节点; 叶子节点为空集合（不是 null, 前端可以少判一次） */
    private List<MenuRes> children;
}
