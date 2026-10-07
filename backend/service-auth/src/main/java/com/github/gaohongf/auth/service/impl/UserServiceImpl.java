package com.github.gaohongf.auth.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.auth.entity.res.PageRes;
import com.github.gaohongf.auth.entity.res.UserAdminRes;
import com.github.gaohongf.auth.cache.CacheKeyConstants;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.dao.RoleDao;
import com.github.gaohongf.auth.dao.UserDao;
import com.github.gaohongf.auth.dao.UserRoleDao;
import com.github.gaohongf.auth.entity.po.RoleEntity;
import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.entity.po.intermediate.UserRoleEntity;
import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.entity.req.GrantRolesCommand;
import com.github.gaohongf.auth.mapstruct.UserConverter;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.satoken.AuthorityChangeNotifier;
import com.github.gaohongf.auth.service.UserService;
import com.github.gaohongf.mybatis.AuditMetaObjectHandler;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@CacheConfig(cacheNames = CacheKeyConstants.USER_TTL + CacheKeyConstants.USER_CACHE_TTL_SECONDS)
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final PermissionDao permissionDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final PasswordEncoder passwordEncoder;
    private final UserConverter userConverter;
    private final AuthorityChangeNotifier authorityChangeNotifier;

    @Override
    public void createUser(CreateUserCommand command) {
        if (userDao.exists(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, command.getUsername()))) {
            R.error(AuthRsm.ACCOUNT_EXISTS);
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(command.getUsername());
        String encodedPassword = passwordEncoder.encode(command.getPassword());
        userEntity.setPassword(encodedPassword);
        userDao.insert(userEntity);
    }

    /**
     * {@code unless = "#result == null"} 不能省。
     * <p>
     * 缓存管理器（{@code CacheConfiguration}）开了 {@code disableCachingNullValues()},
     * 而"查无此人"时本方法就是返回 null —— 不加这个条件, 缓存切面会拿 null 去写缓存,
     * 直接抛 {@code IllegalArgumentException: Cache 'user' is configured to not allow
     * null values but null was provided}, 表现为按不存在的 id 查询时 500。
     * <p>
     * 这里刻意不在 L2 做负缓存: Redis 那份 TTL 是 12 小时, 把一个"此刻不存在"的用户
     * 钉在那里 12 小时, 意味着新建的用户半天看不见。负缓存交给调用方的 L1（TTL 60 秒）。
     */
    @Override
    @Cacheable(key = "#id", unless = "#result == null")
    public UserRes findUser(Long id) {
        UserEntity user = userDao.selectById(id);
        return userConverter.toRes(user);
    }

    /**
     * 注意这里<b>不加</b> {@code @Cacheable}。
     * <p>
     * 两个调用方各有各的缓存, 在这里再叠一层会得到三份可能互相不一致的副本:
     * 本服务的 {@code StpInterfaceImpl} 有本地 Caffeine（快路径, 每请求都会走）,
     * 其他服务的 {@code RemoteStpInterface} 有各自的本地 Caffeine。
     * 这一层是"组装", 保持无状态最好。
     */
    @Override
    public UserAuthorities findAuthorities(Long id) {
        return new UserAuthorities(
                userDao.selectRoleNamesByUserId(id),
                permissionDao.selectPermissionKeysByUserId(id));
    }

    @Override
    public PageRes<UserAdminRes> page(long current, long size, String keyword) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);

        IPage<UserEntity> page = userDao.selectPage(new Page<>(safeCurrent, safeSize),
                Wrappers.<UserEntity>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(UserEntity::getUsername, keyword)
                                .or()
                                .like(UserEntity::getNickname, keyword))
                        .orderByAsc(UserEntity::getId));

        Map<Long, List<String>> rolesByUser = loadRoles(page.getRecords().stream()
                .map(UserEntity::getId)
                .toList());

        List<UserAdminRes> records = page.getRecords().stream()
                .map(user -> new UserAdminRes(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        user.getLocked(),
                        rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();

        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 一次查出这一页所有用户的角色，按 user_id 分组。
     * <p>
     * 不能每个用户查一次：一页 20 个用户就是 20 次查询，而这里一次就够。
     * 逻辑删除条件由 {@code @TableLogic} 自动拼装，不用手写。
     */
    private Map<Long, List<String>> loadRoles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRoleDao.selectList(Wrappers.<UserRoleEntity>lambdaQuery()
                        .in(UserRoleEntity::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(UserRoleEntity::getUserId,
                        Collectors.mapping(UserRoleEntity::getRoleName, Collectors.toList())));
    }

    @Override
    @Transactional
    public void grantRoles(Long userId, GrantRolesCommand command) {
        if (userDao.selectById(userId) == null) {
            R.error(AuthRsm.USER_NOT_FOUND);
        }

        List<String> roleNames = command.getRoleNames() == null
                ? List.of()
                // 去重: 同一个角色传两次会撞 user_roles 的联合主键;
                // 顺带过滤掉空串, 否则会插出一条永远匹配不上 roles 表的孤儿行
                : command.getRoleNames().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (!roleNames.isEmpty()) {
            List<RoleEntity> roles = roleDao.selectBatchIds(roleNames);
            if (roles.size() != roleNames.size()) {
                R.error(AuthRsm.ROLE_NOT_FOUND);
            }
        }

        // 整体替换。清空必须是<b>物理</b>删除: 关联表的主键是 (user_id, role_name) 这对物理组合,
        // 逻辑删除留下的墓碑行会让"同一个角色收回后再授回来"直接撞主键。
        userRoleDao.deleteByUserId(userId);

        if (!roleNames.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            Long operator = AuditMetaObjectHandler.currentOperatorId();
            List<UserRoleEntity> rows = roleNames.stream().map(roleName -> {
                UserRoleEntity row = new UserRoleEntity();
                row.setUserId(userId);
                row.setRoleName(roleName);
                // insertBatch 是自定义 XML, 不走 MyBatis-Plus 的插入路径,
                // 自动填充对它无效, 审计字段必须自己填。见 RoleServiceImpl 里的同款说明。
                row.setCreateBy(operator);
                row.setCreateTime(now);
                row.setUpdateBy(operator);
                row.setUpdateTime(now);
                return row;
            }).toList();
            userRoleDao.insertBatch(rows);
        }

        // 只影响这一个人, 所以只失效他自己的缓存
        authorityChangeNotifier.userChanged(userId);
    }
}
