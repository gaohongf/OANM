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
 *   <li>{@code permissionKey} 来自 {@code permissions} 表的 join, 实体上只有 {@code permissionId}</li>
 * </ul>
 * 硬套 MapStruct 需要写 {@code @Mapping(ignore/expression)}, 比直接手写还难读。
 * 所以这个 DTO 由 {@code MenuServiceImpl} 手工组装。
 */
@Data
public class MenuRes {

    private Long id;

    /** 父节点; {@code null} 表示顶级 */
    private Long parentId;

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

    /** 所需权限的 id; {@code null} 表示登录即可见 */
    private Long permissionId;

    /**
     * 所需权限的键, 由 join {@code permissions} 得来。
     * <p>
     * 一并返回是为了让前端不必再查一次, 也便于在界面上显示"这个菜单被哪个权限挡住"。
     */
    private String permissionKey;

    private Boolean enabled;

    /** 人工锁定; 前端的编辑界面据此判断是否可自由修改 */
    private Boolean locked;

    /** MANUAL / REGISTERED */
    private String source;

    /** 子节点; 叶子节点为空集合（不是 null, 前端可以少判一次） */
    private List<MenuRes> children;
}
