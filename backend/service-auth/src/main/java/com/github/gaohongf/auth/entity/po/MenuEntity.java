package com.github.gaohongf.auth.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单节点。
 *
 * <h2>没有"节点类型"列</h2>
 * 有 {@link #component} 就是可打开的页面, 没有就是只用来分组的目录 —— 这个区分可以从
 * {@code component} 推导出来, 单独存一列只会多一处可能和它不一致的状态。
 * 按钮级权限也不需要菜单行: 前端拿到的是 {@code /api/auth/me} 返回的权限键列表,
 * 按钮直接用键判断。
 *
 * <h2>component 的取值约定</h2>
 * 相对前端 {@code src/pages/} 的路径, 如 {@code ops/work-order/index}。
 * 前端用 {@code import.meta.glob('../pages/**\/*.tsx')} 建白名单按路径索引,
 * 所以后端只能"选一个已存在的页面", 不能指定任意模块。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("menus")
public class MenuEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父节点; {@code null} 表示顶级 */
    private Long parentId;

    /** 显示标题 */
    private String name;

    /** 前端路由, 如 {@code /ops/work-order} */
    private String path;

    /** 前端页面标识(相对 src/pages/ 的路径); 为空即目录 */
    private String component;

    private String icon;

    /** 同级排序, 升序 */
    private Integer sort;

    /** 在侧边栏隐藏, 但仍可路由(用于详情页这类不该出现在菜单里的页面) */
    private Boolean hidden;

    /** 前端是否缓存该页面 */
    private Boolean keepAlive;

    /**
     * 所需权限; {@code null} 表示登录即可见。
     * <p>
     * 用外键而不是权限键字符串: 字符串的失效模式是改名后关联静默断开、菜单失去门禁
     * 变成对所有人可见(fail-open), 外键则改名天然安全。
     */
    private Long permissionId;

    private Boolean enabled;

    /** 人工锁定; 为 true 时自注册不得覆盖 */
    private Boolean locked;

    /** 来源: MANUAL / REGISTERED */
    private String source;
}
