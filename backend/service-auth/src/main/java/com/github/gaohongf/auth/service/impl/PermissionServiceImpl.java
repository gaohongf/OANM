package com.github.gaohongf.auth.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.auth.dao.MenuDao;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.dao.RolePermissionDao;
import com.github.gaohongf.auth.entity.po.MenuEntity;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.req.SavePermissionCommand;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.auth.entity.res.PermissionRes;
import com.github.gaohongf.auth.mapstruct.PermissionConverter;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.satoken.AuthorityChangeNotifier;
import com.github.gaohongf.auth.service.PermissionService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionDao permissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final MenuDao menuDao;
    private final PermissionConverter permissionConverter;
    private final AuthorityChangeNotifier authorityChangeNotifier;

    @Override
    public PageRes<PermissionRes> page(long current, long size, String keyword) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);

        LambdaQueryWrapper<PermissionEntity> wrapper = Wrappers.<PermissionEntity>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PermissionEntity::getPermissionKey, keyword)
                        .or()
                        .like(PermissionEntity::getLabel, keyword))
                .orderByAsc(PermissionEntity::getPermissionKey);

        IPage<PermissionEntity> page = permissionDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(),
                permissionConverter.toResList(page.getRecords()));
    }

    @Override
    public void create(SavePermissionCommand command) {
        rejectSuperKey(command.getPermissionKey());

        if (permissionDao.exists(Wrappers.<PermissionEntity>lambdaQuery()
                .eq(PermissionEntity::getPermissionKey, command.getPermissionKey()))) {
            R.error(AuthRsm.PERMISSION_EXISTS);
        }

        PermissionEntity entity = new PermissionEntity();
        entity.setPermissionKey(command.getPermissionKey());
        entity.setLabel(command.getLabel());
        permissionDao.insert(entity);
        // 新建的权限还没授给任何角色, 不影响任何人的现有权限
    }

    @Override
    public void update(Long id, SavePermissionCommand command) {
        PermissionEntity entity = permissionDao.selectById(id);
        if (entity == null) {
            R.error(AuthRsm.PERMISSION_NOT_FOUND);
        }
        // 内置行的 key 和 label 都不允许改
        rejectSuperKey(entity.getPermissionKey());
        rejectSuperKey(command.getPermissionKey());

        // 改成另一个已存在的 key 会撞 UNIQUE 约束。这里显式查一次, 是为了给出
        // "权限标识已存在"这个明确的提示, 而不是让数据库抛 DuplicateKeyException。
        if (!entity.getPermissionKey().equals(command.getPermissionKey())
                && permissionDao.exists(Wrappers.<PermissionEntity>lambdaQuery()
                .eq(PermissionEntity::getPermissionKey, command.getPermissionKey()))) {
            R.error(AuthRsm.PERMISSION_EXISTS);
        }

        entity.setPermissionKey(command.getPermissionKey());
        entity.setLabel(command.getLabel());
        permissionDao.updateById(entity);

        // key 改了, 所有持有者的权限列表内容就变了
        authorityChangeNotifier.allChanged();
    }

    @Override
    public void delete(Long id) {
        PermissionEntity entity = permissionDao.selectById(id);
        if (entity == null) {
            R.error(AuthRsm.PERMISSION_NOT_FOUND);
        }
        rejectSuperKey(entity.getPermissionKey());

        // 被菜单引用时拒绝。不这么做的话, 菜单那一行的 permission_id 会指向一个
        // 已删除的权限 —— 它的 key 查不出来, 菜单就失去了门禁, 变成对所有人可见。
        // 这是 fail-open, 比报错严重得多, 所以宁可让管理员先解除引用。
        if (menuDao.exists(Wrappers.<MenuEntity>lambdaQuery().eq(MenuEntity::getPermissionId, id))) {
            R.error(AuthRsm.PERMISSION_IN_USE);
        }

        permissionDao.deleteById(id);
        // 物理删除关联行 —— 逻辑删除的墓碑会让这个权限日后重新授予时撞主键,
        // 见 RolePermissionDao 的说明
        rolePermissionDao.deleteByPermissionId(id);

        authorityChangeNotifier.allChanged();
    }

    /**
     * 拒绝把 {@code *} 当作普通权限来创建/改名/删除。
     * <p>
     * 内置的超管权限只应由 {@code db/seed.sql} 产生。允许通过接口造出它,
     * 就等于给"能管理权限的角色"开了一条提权路径 —— 造一行 {@code *} 再授给自己即可。
     */
    private static void rejectSuperKey(String permissionKey) {
        if (PermissionEntity.SUPER_KEY.equals(permissionKey)) {
            R.error(AuthRsm.SUPER_PERMISSION_IMMUTABLE);
        }
    }
}
