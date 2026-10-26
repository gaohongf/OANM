package com.github.gaohongf.auth.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.auth.dao.MenuDao;
import com.github.gaohongf.auth.dao.MenuPermissionDao;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.entity.po.MenuEntity;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.po.intermediate.MenuPermissionEntity;
import com.github.gaohongf.auth.entity.req.SaveMenuCommand;
import com.github.gaohongf.auth.entity.res.MenuRes;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.MenuService;
import com.github.gaohongf.mybatis.AuditMetaObjectHandler;
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
    private final MenuPermissionDao menuPermissionDao;

    @Override
    public List<MenuRes> tree() {
        List<MenuEntity> all = loadAllMenus();
        Map<Long, List<Long>> permissionIdsByMenu = loadMenuPermissionIds(all);
        Map<Long, String> permissionKeys = loadPermissionKeys(permissionIdsByMenu);
        return buildTree(all, null, permissionIdsByMenu, permissionKeys);
    }

    @Override
    public List<MenuRes> treeForCurrentUser() {
        // 三次查询: 菜单、关联、权限键。先把它们算好再往下传,
        // 避免下层方法各自再查一遍（早期写法在树的每一层都重查了一次权限表）。
        List<MenuEntity> all = loadAllMenus();
        Map<Long, List<Long>> permissionIdsByMenu = loadMenuPermissionIds(all);
        Map<Long, String> permissionKeys = loadPermissionKeys(permissionIdsByMenu);
        Set<Long> visibleIds = visibleIdsForCurrentUser(all, permissionIdsByMenu, permissionKeys);
        return buildTree(all, visibleIds, permissionIdsByMenu, permissionKeys);
    }

    @Override
    @Transactional
    public void create(SaveMenuCommand command) {
        command.check();
        requireParentExists(command.getParentId());
        List<Long> permissionIds = resolvePermissionIds(command);

        MenuEntity entity = new MenuEntity();
        applyCommand(entity, command);
        entity.setLocked(false);
        entity.setSource("MANUAL");
        menuDao.insert(entity);

        replaceMenuPermissions(entity.getId(), permissionIds);
    }

    @Override
    @Transactional
    public void update(Long id, SaveMenuCommand command) {
        command.check();
        MenuEntity entity = menuDao.selectById(id);
        if (entity == null) {
            R.error(AuthRsm.MENU_NOT_FOUND);
        }

        requireParentExists(command.getParentId());
        guardNoCycle(id, command.getParentId());
        List<Long> permissionIds = resolvePermissionIds(command);

        applyCommand(entity, command);
        menuDao.updateById(entity);
        replaceMenuPermissions(id, permissionIds);
    }

    @Override
    @Transactional
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

        // 先物理清掉关联, 再逻辑删菜单。关联行留着没有任何意义, 也会留下孤儿数据。
        menuPermissionDao.deleteByMenuId(id);
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
    private Set<Long> visibleIdsForCurrentUser(List<MenuEntity> all,
            Map<Long, List<Long>> permissionIdsByMenu, Map<Long, String> permissionKeys) {
        return all.stream()
                // 停用的节点对普通用户不可见。管理端不过滤, 否则管理员无法为别人配置它们。
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .filter(m -> isVisibleForCurrentUser(m, permissionIdsByMenu, permissionKeys))
                .map(MenuEntity::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 单个节点对当前用户是否可见。
     * <p>
     * 没有任何关联权限表示"登录即可见"; 有多个时<b>任意一个</b>命中即可(OR)。
     * 某一条关联的权限行查不出来时, 这一条不授权(fail-closed), 但不影响其他关联 ——
     * 只有全部关联都查不出来时才整体拒绝。
     */
    private static boolean isVisibleForCurrentUser(MenuEntity menu,
            Map<Long, List<Long>> permissionIdsByMenu, Map<Long, String> permissionKeys) {
        List<Long> permissionIds = permissionIdsByMenu.getOrDefault(menu.getId(), List.of());
        for (Long permissionId : permissionIds) {
            String key = permissionKeys.get(permissionId);
            if (key != null && StpUtil.hasPermission(key)) {
                return true;
            }
        }
        return permissionIds.isEmpty();
    }

    /**
     * 读出每个菜单的门禁权限 id。
     * <p>
     * 关联表一次查完再分组, 避免每个节点查一次(N+1)。迁移期还要并上只读的旧列
     * {@code permission_id}: 如果 seed 的搬迁还没执行, 只认关联表会让原本受控的菜单
     * 变成"登录即可见"。
     *
     * @return 只包含有门禁的菜单; 值是升序去重后的 id 列表
     */
    private Map<Long, List<Long>> loadMenuPermissionIds(List<MenuEntity> menus) {
        Map<Long, List<Long>> grouped = menuPermissionDao.selectList(
                        Wrappers.<MenuPermissionEntity>lambdaQuery()
                                .orderByAsc(MenuPermissionEntity::getMenuId)
                                .orderByAsc(MenuPermissionEntity::getPermissionId))
                .stream()
                .collect(Collectors.groupingBy(
                        MenuPermissionEntity::getMenuId,
                        Collectors.mapping(MenuPermissionEntity::getPermissionId, Collectors.toList())));

        Map<Long, List<Long>> result = new HashMap<>();
        for (MenuEntity menu : menus) {
            Set<Long> ids = new TreeSet<>(grouped.getOrDefault(menu.getId(), List.of()));
            if (menu.getPermissionId() != null) {
                ids.add(menu.getPermissionId());
            }
            if (!ids.isEmpty()) {
                result.put(menu.getId(), List.copyOf(ids));
            }
        }
        return result;
    }

    /**
     * 一次查出所有涉及到的权限键。
     * <p>
     * 逐个节点查会变成 N+1 次查询, 而菜单树一次要组装几十个节点。
     */
    private Map<Long, String> loadPermissionKeys(Map<Long, List<Long>> permissionIdsByMenu) {
        List<Long> permissionIds = permissionIdsByMenu.values().stream()
                .flatMap(List::stream)
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
    private List<MenuRes> buildTree(List<MenuEntity> all, Set<Long> visibleIds,
            Map<Long, List<Long>> permissionIdsByMenu, Map<Long, String> permissionKeys) {
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

        return assemble(byParent.getOrDefault(ROOT, List.of()), byParent,
                permissionIdsByMenu, permissionKeys, 0);
    }

    private List<MenuRes> assemble(
            List<MenuEntity> level,
            Map<Long, List<MenuEntity>> byParent,
            Map<Long, List<Long>> permissionIdsByMenu,
            Map<Long, String> permissionKeys,
            int depth) {

        if (depth > MAX_DEPTH) {
            // 走到这里说明库里已经有环（正常数据不可能有这么多层）。
            // 直接报错而不是截断 —— 截断会让人以为"菜单就是这样", 从而永远发现不了数据损坏。
            R.error(AuthRsm.MENU_TREE_TOO_DEEP);
        }

        List<MenuRes> result = new ArrayList<>(level.size());
        for (MenuEntity entity : level) {
            MenuRes res = toRes(entity, permissionIdsByMenu, permissionKeys);
            res.setChildren(assemble(
                    byParent.getOrDefault(entity.getId(), List.of()), byParent,
                    permissionIdsByMenu, permissionKeys, depth + 1));
            result.add(res);
        }
        return result;
    }

    private static MenuRes toRes(MenuEntity entity,
            Map<Long, List<Long>> permissionIdsByMenu, Map<Long, String> permissionKeys) {
        MenuRes res = new MenuRes();
        res.setId(idText(entity.getId()));
        res.setParentId(idText(entity.getParentId()));
        res.setName(entity.getName());
        res.setPath(entity.getPath());
        res.setComponent(entity.getComponent());
        res.setIcon(entity.getIcon());
        res.setSort(entity.getSort());
        res.setHidden(entity.getHidden());
        res.setKeepAlive(entity.getKeepAlive());
        List<Long> permissionIds = permissionIdsByMenu.getOrDefault(entity.getId(), List.of());
        res.setPermissionIds(permissionIds.stream().map(MenuServiceImpl::idText).toList());
        // 允许 null 元素: 某条关联的权限行可能已经不存在。这里不能过滤,
        // 否则 permissionKeys 会比 permissionIds 短, 前端按下标取键就错位了。
        res.setPermissionKeys(permissionIds.stream().map(permissionKeys::get).toList());
        res.setEnabled(entity.getEnabled());
        res.setLocked(entity.getLocked());
        res.setSource(entity.getSource());
        res.setChildren(List.of());
        return res;
    }

    /**
     * 雪花 id 出网必须走字符串, 理由见 {@code MenuRes} 的类注释。
     * <p>
     * 不能直接用 {@code String.valueOf}: 这里的 {@code parentId} 可空(顶级菜单),
     * 而 {@code String.valueOf((Long) null)} 返回的是字符串 {@code "null"} ——
     * 前端会当真, 拿它去当父节点 id。编译期不会有任何提示, 所以这个转换必须集中在这里做。
     */
    private static String idText(Long id) {
        return id == null ? null : String.valueOf(id);
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
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
    }

    /**
     * 规范化并校验命令里的门禁权限集合。
     * <p>
     * 兼容旧前端的单个 {@code permissionId}: 只有新字段为 {@code null} 时才使用它,
     * 避免发布窗口里旧页面保存菜单时把门禁清空。
     */
    private List<Long> resolvePermissionIds(SaveMenuCommand command) {
        List<Long> requested = command.getPermissionIds();
        if (requested == null && command.getPermissionId() != null) {
            requested = List.of(command.getPermissionId());
        }
        List<Long> permissionIds = requested == null
                ? List.of()
                : requested.stream().filter(Objects::nonNull).distinct().sorted().toList();

        if (!permissionIds.isEmpty()
                && permissionDao.selectBatchIds(permissionIds).size() != permissionIds.size()) {
            // 有 id 查不出来: 可能是并发删除, 也可能是客户端传了脏数据。
            // 逻辑删除的权限也会被 selectBatchIds 过滤掉, 同样拒绝。
            R.error(AuthRsm.PERMISSION_NOT_FOUND);
        }
        return permissionIds;
    }

    /**
     * 整体替换某个菜单的门禁权限。
     * <p>
     * 先物理清空再写入 —— 关联表的主键是 {@code (menu_id, permission_id)},
     * 逻辑删除的墓碑会让"取消后再挂回来"撞主键(同 role_permissions)。
     */
    private void replaceMenuPermissions(Long menuId, List<Long> permissionIds) {
        menuPermissionDao.deleteByMenuId(menuId);
        if (permissionIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Long operator = AuditMetaObjectHandler.currentOperatorId();
        List<MenuPermissionEntity> rows = permissionIds.stream().map(permissionId -> {
            MenuPermissionEntity row = new MenuPermissionEntity();
            row.setMenuId(menuId);
            row.setPermissionId(permissionId);
            // 必须手工填: insertBatch 是裸 XML, 不走 AuditMetaObjectHandler 的自动填充。
            row.setCreateBy(operator);
            row.setCreateTime(now);
            row.setUpdateBy(operator);
            row.setUpdateTime(now);
            return row;
        }).toList();
        menuPermissionDao.insertBatch(rows);
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
                return; // R.error 一定抛异常, 这里只是让编译器知道不会继续往下走
            }
            cursor = parent.getParentId();
        }
    }
}
