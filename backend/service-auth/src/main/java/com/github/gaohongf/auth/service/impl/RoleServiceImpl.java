package com.github.gaohongf.auth.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.dao.RoleDao;
import com.github.gaohongf.auth.dao.RolePermissionDao;
import com.github.gaohongf.auth.dao.UserRoleDao;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.po.RoleEntity;
import com.github.gaohongf.auth.entity.po.intermediate.RolePermissionEntity;
import com.github.gaohongf.auth.entity.po.intermediate.UserRoleEntity;
import com.github.gaohongf.auth.entity.req.GrantPermissionsCommand;
import com.github.gaohongf.auth.entity.req.SaveRoleCommand;
import com.github.gaohongf.auth.entity.res.PermissionRes;
import com.github.gaohongf.auth.entity.res.RoleRes;
import com.github.gaohongf.auth.mapstruct.PermissionConverter;
import com.github.gaohongf.auth.mapstruct.RoleConverter;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.satoken.AuthorityChangeNotifier;
import com.github.gaohongf.auth.service.RoleService;
import com.github.gaohongf.mybatis.AuditMetaObjectHandler;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleDao roleDao;
    private final RolePermissionDao rolePermissionDao;
    private final UserRoleDao userRoleDao;
    private final PermissionDao permissionDao;
    private final RoleConverter roleConverter;
    private final PermissionConverter permissionConverter;
    private final AuthorityChangeNotifier authorityChangeNotifier;

    @Override
    public List<RoleRes> list() {
        return roleConverter.toResList(roleDao.selectList(
                Wrappers.<RoleEntity>lambdaQuery().orderByAsc(RoleEntity::getRoleName)));
    }

    @Override
    public List<PermissionRes> findPermissions(String roleName) {
        if (roleDao.selectById(roleName) == null) {
            R.error(AuthRsm.ROLE_NOT_FOUND);
        }
        // 复用已有的 join 查询（RoleDao.selectPermissionsByRoleName），不用自己拼
        return permissionConverter.toResList(roleDao.selectPermissionsByRoleName(roleName));
    }

    @Override
    public void create(SaveRoleCommand command) {
        if (roleDao.exists(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getRoleName, command.getRoleName()))) {
            R.error(AuthRsm.ROLE_EXISTS);
        }

        RoleEntity entity = new RoleEntity();
        entity.setRoleName(command.getRoleName());
        entity.setLabel(command.getLabel());
        // 审计字段由 AuditMetaObjectHandler 自动填充(走的是 BaseMapper, 不是裸 XML)
        roleDao.insert(entity);
        // 新增角色不影响任何人的现有权限, 所以这里不需要失效缓存
    }

    @Override
    public void update(String roleName, SaveRoleCommand command) {
        RoleEntity entity = roleDao.selectById(roleName);
        if (entity == null) {
            R.error(AuthRsm.ROLE_NOT_FOUND);
        }

        // 只改 label。role_name 是主键且被 user_roles / role_permissions 以外键引用,
        // 改名等于改主键 + 级联两张表, 所以接口上不提供这个能力。
        entity.setLabel(command.getLabel());
        roleDao.updateById(entity);
        // label 只是显示名, 不参与授权判断, 所以不需要失效缓存
    }

    @Override
    public void delete(String roleName) {
        RoleEntity entity = roleDao.selectById(roleName);
        if (entity == null) {
            R.error(AuthRsm.ROLE_NOT_FOUND);
        }

        // 被用户持有时拒绝 —— 直接删掉会让那些用户莫名其妙少一批权限,
        // 而且他们的 user_roles 行会变成指向已删除角色的孤儿数据。
        if (userRoleDao.exists(Wrappers.<UserRoleEntity>lambdaQuery()
                .eq(UserRoleEntity::getRoleName, roleName))) {
            R.error(AuthRsm.ROLE_IN_USE);
        }

        roleDao.deleteById(roleName);
        // 连同该角色的授权一起清掉, 否则留下指向已删除角色的孤儿行。
        // 这里是物理删除 —— 关联表的逻辑删除会让"收回后再授回来"撞主键, 见 RolePermissionDao。
        rolePermissionDao.deleteByRoleName(roleName);

        // 角色没了, 它的持有者(不应该有, 上面已经拦过)权限会变, 保守起见全量失效
        authorityChangeNotifier.allChanged();
    }

    @Override
    @Transactional
    public void grantPermissions(String roleName, GrantPermissionsCommand command) {
        RoleEntity role = roleDao.selectById(roleName);
        if (role == null) {
            R.error(AuthRsm.ROLE_NOT_FOUND);
        }

        List<Long> permissionIds = command.getPermissionIds() == null
                ? List.of()
                // 去重: 同一个 id 传两次会撞 role_permissions 的联合主键
                : command.getPermissionIds().stream().filter(Objects::nonNull).distinct().toList();

        if (!permissionIds.isEmpty()) {
            List<PermissionEntity> permissions = permissionDao.selectBatchIds(permissionIds);
            if (permissions.size() != permissionIds.size()) {
                // 有 id 查不出来 —— 可能是并发删除, 也可能是客户端传了脏数据
                R.error(AuthRsm.PERMISSION_NOT_FOUND);
            }
            rejectSuperGrantUnlessHolding(permissions);
        }

        // 整体替换: 先清空该角色的全部授权, 再写入新的一份。
        // 清空必须是<b>物理</b>删除: 关联表的主键是 (role_name, permission_id) 这对物理组合,
        // 逻辑删除留下的墓碑行会让"同一个权限收回后再授回来"直接撞主键。
        rolePermissionDao.deleteByRoleName(roleName);

        if (!permissionIds.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            Long operator = AuditMetaObjectHandler.currentOperatorId();
            List<RolePermissionEntity> rows = permissionIds.stream().map(permissionId -> {
                RolePermissionEntity row = new RolePermissionEntity();
                row.setRoleName(roleName);
                row.setPermissionId(permissionId);
                // 必须手工填审计字段: insertBatch 是自定义 XML, 走的是裸 SQL,
                // AuditMetaObjectHandler 的自动填充对它不生效。不填的话 create_time 等
                // 列进去就是 NULL（这几列在库中可空, 所以不会报错, 只会静默丢失审计信息）。
                row.setCreateBy(operator);
                row.setCreateTime(now);
                row.setUpdateBy(operator);
                row.setUpdateTime(now);
                return row;
            }).toList();
            rolePermissionDao.insertBatch(rows);
        }

        // 改的是角色, 影响所有持有它的人 —— 全量失效
        authorityChangeNotifier.allChanged();
    }

    /**
     * 拒绝把超管权限授出去, 除非调用者自己就持有它。
     * <p>
     * 没有这道检查的话, 一个只有"管理权限"权限的角色可以先给自己授上 {@code *},
     * 完成提权。注意这里的判断走的是 sa-token 自己的匹配, 所以
     * {@code hasPermission("*")} 对持有 {@code *} 的人为 true、对只持有普通权限的人为 false。
     * <p>
     * 取不到登录态时<b>拒绝</b>（fail-closed）—— 这个判断一旦放行就是提权, 所以宁可误拒。
     */
    private static void rejectSuperGrantUnlessHolding(List<PermissionEntity> permissions) {
        boolean grantingSuper = permissions.stream()
                .anyMatch(p -> PermissionEntity.SUPER_KEY.equals(p.getPermissionKey()));
        if (grantingSuper && !StpUtil.hasPermission(PermissionEntity.SUPER_KEY)) {
            R.error(AuthRsm.GRANT_SUPER_PERMISSION_DENIED);
        }
    }
}
