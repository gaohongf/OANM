package com.github.gaohongf.auth.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 权限表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("permissions")
public class PermissionEntity extends BaseEntity {

    /**
     * 超级管理员权限的键：单个 {@code *}。
     *
     * <h3>为什么这一个键就能匹配一切</h3>
     * sa-token 的匹配是"用户的权限列表是模式列表, 传入的请求是目标":
     * {@code SaStrategy.hasElement} 先做精确匹配, 未命中则用
     * {@code SaFoxUtil.vagueMatch(列表元素, 传入参数)} 逐个比对, 而 {@code *}
     * 匹配任意长度（含 0）的字符。所以持有 {@code *} 的人,
     * {@code hasPermission("GET:/api/ops/work_order/{id}")} 恒为 true。
     *
     * <h3>它是数据行, 不是代码里的特殊角色</h3>
     * 好处是"谁能做什么"完全由数据表达, 拦截器里没有任何硬编码的超管分支。
     * 代价是它可被伪造 —— 一个只有"管理权限"权限的角色可以造一行 {@code *}
     * 再授给自己, 完成提权。所以服务层对它有两道保护:
     * <ul>
     *   <li>这一行不允许改名或删除（见 {@code AuthRsm.SUPER_PERMISSION_IMMUTABLE}）</li>
     *   <li>只有已经持有它的人才能把它授给角色（见 {@code AuthRsm.GRANT_SUPER_PERMISSION_DENIED}）</li>
     * </ul>
     */
    public static final String SUPER_KEY = "*";

    /**
     * 唯一编号 避免使用permissionKey直接泄露
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 权限标识（唯一）
     */
    private String permissionKey;
    /**
     * 标签
     */
    private String label;
}
