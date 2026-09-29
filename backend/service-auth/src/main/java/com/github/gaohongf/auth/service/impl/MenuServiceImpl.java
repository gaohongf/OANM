package com.github.gaohongf.auth.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.MenuDao;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.entity.po.MenuEntity;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.req.SaveMenuCommand;
import com.github.gaohongf.auth.entity.res.MenuRes;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.MenuService;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class MenuServiceImpl implements MenuService {

    /**
     * 顶级节点的分组键。
     * <p>
     * 用 0 而不是 null 作 map 的键, 因为 {@code LinkedHashMap} 支持 null 键但读起来容易出错。
     * 雪花 id 永远为正数, 所以 0 不可能和任何真实节点冲突。
     */
    private static final long ROOT = 0L;

    /**
     * 树的最大深度。
     * <p>
     * 这个上限不是产品限制, 而是<b>防死循环的保险</b>: 库里一旦已经存在环（例如有人直接改库
     * 造出来的）, 递归组装会无限下去。有了上限, 症状是一条明确的报错, 而不是请求挂死。
     */
    private static final int MAX_DEPTH = 32;

    private final MenuDao menuDao;
    private final PermissionDao permissionDao;

    @Override
    public List<MenuRes> tree() {
        List<MenuEntity> all = loadAllMenus();
        return buildTree(all, null, loadPermissionKeys(all));
    }

    @Override
    public List<MenuRes> treeForCurrentUser() {
        // 三次查询就够: 菜单一次、权限一次。这里刻意把菜单和权限键都先算好再传下去,
        // 避免下层方法各自再查一遍（早期写法在树的每一层都重查了一次权限表）。
        List<MenuEntity> all = loadAllMenus();
        Map<Long, String> permissionKeys = loadPermissionKeys(all);
        return buildTree(all, visibleIdsForCurrentUser(all, permissionKeys), permissionKeys);
    }

    @Override
    public void create(SaveMenuCommand command) {
        command.check();
        requireParentExists(command.getParentId());

        MenuEntity entity = new MenuEntity();
        applyCommand(entity, command);
        entity.setLocked(false);
        entity.setSource("MANUAL");
        menuDao.insert(entity);
    }

    @Override
    public void update(Long id, SaveMenuCommand command) {
        command.check();
        MenuEntity entity = menuDao.selectById(id);
        if (entity == null) {
            R.error(AuthRsm.MENU_NOT_FOUND);
        }

        requireParentExists(command.getParentId());
        guardNoCycle(id, command.getParentId());

        applyCommand(entity, command);
        menuDao.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        if (menuDao.selectById(id) == null) {
            R.error(AuthRsm.MENU_NOT_FOUND);
        }

        // 有子节点时拒绝。不做静默级联 —— 静默删掉一整棵子树是运维事故,
        // 而且删完才发现菜单少了东西时已经很难还原。
        boolean hasChildren = menuDao.exists(Wrappers.<MenuEntity>lambdaQuery()
                .eq(MenuEntity::getParentId, id));
        if (hasChildren) {
            R.error(AuthRsm.MENU_HAS_CHILDREN);
        }

        menuDao.deleteById(id);
    }

    // ---------------------------------------------------------------- 内部

    private List<MenuEntity> loadAllMenus() {
        return menuDao.selectList(Wrappers.<MenuEntity>lambdaQuery()
                .orderByAsc(MenuEntity::getSort)
                .orderByAsc(MenuEntity::getId));
    }

    /**
     * 算出当前用户可见的节点 id 集合。
     */
    private Set<Long> visibleIdsForCurrentUser(List<MenuEntity> all, Map<Long, String> permissionKeys) {
        return all.stream()
                // 停用的节点对普通用户不可见。管理端不过滤, 否则管理员无法为别人配置它们。
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .filter(m -> isVisibleForCurrentUser(m, permissionKeys))
                .map(MenuEntity::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 单个节点对当前用户是否可见。
     * <p>
     * {@code permissionId} 为 null 表示"登录即可见"。
     * <b>注意 key 查不出来时是拒绝, 不是放行</b> —— 那说明它的权限行已经被删了,
     * 放行等于让一个本该受限的菜单对所有人可见（fail-open）。
     */
    private static boolean isVisibleForCurrentUser(MenuEntity menu, Map<Long, String> permissionKeys) {
        if (menu.getPermissionId() == null) {
            return true;
        }
        String key = permissionKeys.get(menu.getPermissionId());
        if (key == null) {
            return false;
        }
        return StpUtil.hasPermission(key);
    }

    /**
     * 一次查出所有涉及到的权限键。
     * <p>
     * 逐个节点查会变成 N+1 次查询, 而菜单树一次要组装几十个节点。
     */
    private Map<Long, String> loadPermissionKeys(List<MenuEntity> menus) {
        List<Long> permissionIds = menus.stream()
                .map(MenuEntity::getPermissionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return Map.of();
        }
        return permissionDao.selectBatchIds(permissionIds).stream()
                .collect(Collectors.toMap(PermissionEntity::getId, PermissionEntity::getPermissionKey));
    }

    /**
     * 按 parent_id 组装树。
     *
     * @param visibleIds 允许出现的节点 id; {@code null} 表示全部允许
     */
    private List<MenuRes> buildTree(List<MenuEntity> all, Set<Long> visibleIds, Map<Long, String> permissionKeys) {
        // 先过滤再分组: 父节点被过滤掉时, 它的子节点会落在一个永远遍历不到的桶里,
        // 从而自然消失。这正是想要的 —— 父菜单都看不见了, 子菜单还能看到才奇怪。
        List<MenuEntity> visible = all.stream()
                .filter(m -> visibleIds == null || visibleIds.contains(m.getId()))
                .toList();

        Map<Long, List<MenuEntity>> byParent = visible.stream()
                .sorted(Comparator.comparing(MenuEntity::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MenuEntity::getId))
                .collect(Collectors.groupingBy(
                        m -> m.getParentId() == null ? ROOT : m.getParentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return assemble(byParent.getOrDefault(ROOT, List.of()), byParent, permissionKeys, 0);
    }

    private List<MenuRes> assemble(
            List<MenuEntity> level,
            Map<Long, List<MenuEntity>> byParent,
            Map<Long, String> permissionKeys,
            int depth) {

        if (depth > MAX_DEPTH) {
            // 走到这里说明库里已经有环（正常数据不可能有这么多层）。
            // 直接报错而不是截断 —— 截断会让人以为"菜单就是这样", 从而永远发现不了数据损坏。
            R.error(AuthRsm.MENU_TREE_TOO_DEEP);
        }

        List<MenuRes> result = new ArrayList<>(level.size());
        for (MenuEntity entity : level) {
            MenuRes res = toRes(entity, permissionKeys);
            res.setChildren(assemble(
                    byParent.getOrDefault(entity.getId(), List.of()), byParent, permissionKeys, depth + 1));
            result.add(res);
        }
        return result;
    }

    private static MenuRes toRes(MenuEntity entity, Map<Long, String> permissionKeys) {
        MenuRes res = new MenuRes();
        res.setId(entity.getId());
        res.setParentId(entity.getParentId());
        res.setName(entity.getName());
        res.setPath(entity.getPath());
        res.setComponent(entity.getComponent());
        res.setIcon(entity.getIcon());
        res.setSort(entity.getSort());
        res.setHidden(entity.getHidden());
        res.setKeepAlive(entity.getKeepAlive());
        res.setPermissionId(entity.getPermissionId());
        // 必须先判 null 再 get。permissionId 为 null 表示"登录即可见", 是常见情况;
        // 而 loadPermissionKeys 在没有权限时返回 Map.of(), 那是<b>不可变</b> Map ——
        // 它的 get(null) 会抛 NullPointerException（ImmutableCollections.MapN 里有 requireNonNull）,
        // 表现为"菜单树接口 500, 但只要有一个菜单配了权限就正常", 很难联想到是空 Map 的问题。
        res.setPermissionKey(entity.getPermissionId() == null
                ? null
                : permissionKeys.get(entity.getPermissionId()));
        res.setEnabled(entity.getEnabled());
        res.setLocked(entity.getLocked());
        res.setSource(entity.getSource());
        res.setChildren(List.of());
        return res;
    }

    private void applyCommand(MenuEntity entity, SaveMenuCommand command) {
        entity.setParentId(command.getParentId());
        entity.setName(command.getName());
        entity.setPath(command.getPath());
        entity.setComponent(command.getComponent());
        entity.setIcon(command.getIcon());
        entity.setSort(command.getSort() == null ? 0 : command.getSort());
        entity.setHidden(Boolean.TRUE.equals(command.getHidden()));
        entity.setKeepAlive(Boolean.TRUE.equals(command.getKeepAlive()));
        entity.setPermissionId(command.getPermissionId());
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
    }

    private void requireParentExists(Long parentId) {
        if (parentId != null && menuDao.selectById(parentId) == null) {
            R.error(AuthRsm.MENU_NOT_FOUND);
        }
    }

    /**
     * 防止把节点挂到自己的后代下面（会形成环, 之后建树就是死循环）。
     * <p>
     * 做法是从目标父节点沿着 parent_id 往上走, 遇到被移动的节点本身就说明会成环。
     * 遍历带有深度上限: 库里可能<b>已经</b>存在环（比如有人直接改库造成的）,
     * 没有上限的话这段"防环"的代码自己会挂死。
     */
    private void guardNoCycle(Long id, Long newParentId) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(id)) {
            R.error(AuthRsm.MENU_PARENT_IS_SELF);
        }

        Long cursor = newParentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(id)) {
                R.error(AuthRsm.MENU_PARENT_IS_DESCENDANT);
            }
            if (++depth > MAX_DEPTH) {
                R.error(AuthRsm.MENU_TREE_TOO_DEEP);
            }
            MenuEntity parent = menuDao.selectById(cursor);
            if (parent == null) {
                // 祖先链断了, 说明数据已经不一致, 不该继续移动
                R.error(AuthRsm.MENU_NOT_FOUND);
            }
            cursor = parent.getParentId();
        }
    }
}
