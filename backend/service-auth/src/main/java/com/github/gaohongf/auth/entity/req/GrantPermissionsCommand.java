package com.github.gaohongf.auth.entity.req;

import java.util.List;

import lombok.Data;

/**
 * 给角色授权。
 *
 * <h2>整体替换语义</h2>
 * 传进来的是"这个角色最终应该拥有的权限全集", 不是增量。所以空列表表示<b>收回该角色的
 * 全部权限</b>, 而不是"不改动"。
 * <p>
 * 选整体替换而不是增删两项, 是因为增删语义在界面上容易产生竞态: 两个人同时操作同一角色时,
 * "加上 A"和"去掉 B"的先后顺序会让最终结果取决于到达顺序。整体替换的最终状态由最后一次请求
 * 唯一确定, 出问题时也只需要看最后一个请求。
 */
@Data
public class GrantPermissionsCommand {

    /** 目标权限 id 集合; 传空集合表示清空该角色的全部权限 */
    private List<Long> permissionIds;
}
