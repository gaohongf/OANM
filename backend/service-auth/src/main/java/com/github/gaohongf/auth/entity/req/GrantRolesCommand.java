package com.github.gaohongf.auth.entity.req;

import java.util.List;

import lombok.Data;

/**
 * 给用户授角色。
 * <p>
 * 与 {@link GrantPermissionsCommand} 同样是<b>整体替换</b>: 传进来的是该用户最终应该拥有的
 * 角色全集, 空列表表示收回全部角色。理由见那个类的说明。
 */
@Data
public class GrantRolesCommand {

    /** 目标角色名集合; 传空集合表示收回该用户的全部角色 */
    private List<String> roleNames;
}
