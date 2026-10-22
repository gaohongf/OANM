package com.github.gaohongf.auth.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.auth.entity.res.UserAdminRes;
import com.github.gaohongf.auth.cache.CacheKeyConstants;
import com.github.gaohongf.auth.dao.PermissionDao;
import com.github.gaohongf.auth.dao.RoleDao;
import com.github.gaohongf.auth.dao.UserDao;
import com.github.gaohongf.auth.dao.UserRoleDao;
import com.github.gaohongf.auth.entity.po.RoleEntity;
import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.entity.po.intermediate.UserRoleEntity;
import com.github.gaohongf.auth.entity.req.ChangeOwnNicknameCommand;
import com.github.gaohongf.auth.entity.req.ChangeOwnPasswordCommand;
import com.github.gaohongf.auth.entity.req.CreateUserCommand;
import com.github.gaohongf.auth.entity.req.GrantRolesCommand;
import com.github.gaohongf.auth.entity.req.UpdateUserLockedCommand;
import com.github.gaohongf.auth.mapstruct.UserConverter;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.satoken.AuthorityChangeNotifier;
import com.github.gaohongf.auth.service.UserService;
import com.github.gaohongf.mybatis.AuditMetaObjectHandler;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
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
        command.check();

        // 用 count 而不是 exists: 唯一索引不区分逻辑删除, 一条已删除的同名"墓碑行"
        // 同样会挡住 insert。exists 会因为 @TableLogic 自动加 deleted = 0 而漏掉它,
        // 于是把本可以明确提示的"账户已存在"变成数据库异常。
        if (userDao.countByUsernameIncludingDeleted(command.getUsername()) > 0) {
            R.error(AuthRsm.ACCOUNT_EXISTS);
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(command.getUsername());
        userEntity.setPassword(passwordEncoder.encode(command.getPassword()));
        // 昵称留空, 前端显示时回落到用户名; locked 显式给 false, 不依赖列默认值
        userEntity.setNickname("");
        userEntity.setLocked(false);
        try {
            userDao.insert(userEntity);
        } catch (DuplicateKeyException e) {
            // 两个管理员同时创建同名用户时, 上面的 count 可能都返回 0, 唯一索引是最后一道防线
            R.error(AuthRsm.ACCOUNT_EXISTS);
        }
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

    /**
     * 刻意<b>不加 {@code @Cacheable}</b>：收件人列表是"发通知的那一瞬间"的快照，
     * 缓存它意味着"刚给某人授了 admin，他的通知却要等缓存过期才发得出去"。
     * 调用方（生产方的发布器）本来就不是高频路径 —— 建单这类业务动作才触发一次。
     */
    @Override
    public List<Long> findUserIdsByRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return List.of();
        }
        return userDao.selectUserIdsByRole(roleName);
    }

    @Override
    public PageRes<UserAdminRes> page(long current, long size, String keyword, String permissionKey) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);

        LambdaQueryWrapper<UserEntity> wrapper = Wrappers.<UserEntity>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(UserEntity::getUsername, keyword)
                        .or()
                        .like(UserEntity::getNickname, keyword));

        if (StringUtils.hasText(permissionKey)) {
            List<Long> userIds = userDao.selectUserIdsByPermission(permissionKey.trim());
            if (userIds.isEmpty()) {
                // 提前返回而不是把空集合交给 in(): 空 in 在不同版本里可能被渲染成非法的
                // `IN ()`, 而且"没有人持有该权限"本来就是正常结果, 不是查询失败。
                return new PageRes<>(0, safeCurrent, safeSize, List.of());
            }
            wrapper.in(UserEntity::getId, userIds);
        }

        wrapper.orderByAsc(UserEntity::getId);

        IPage<UserEntity> page = userDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);

        Map<Long, List<String>> rolesByUser = loadRoles(page.getRecords().stream()
                .map(UserEntity::getId)
                .toList());

        List<UserAdminRes> records = page.getRecords().stream()
                .map(user -> new UserAdminRes(
                        // 雪花 id 出网必须走字符串, 理由见 UserAdminRes 的类注释。查到即有主键, 不用判空
                        String.valueOf(user.getId()),
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

    /**
     * 封禁 / 解封。
     * <p>
     * 三个方法都刻意不加 {@code @Transactional}: 都是单行更新, 而且真正需要"事后再做"的
     * 踢会话/注销会话不能被回滚。若放在事务里, 踢人可能先于数据库提交发生, 用户在这几毫秒内
     * 重新登录就会拿到一个"已封禁却有效"的会话。
     * <p>
     * {@code @CacheEvict} 清掉 {@link #findUser} 的 12 小时 Redis 缓存, 否则封禁后
     * 本服务返回的用户信息里 locked 还是旧值。
     */
    @Override
    @CacheEvict(key = "#id")
    public void updateLocked(Long id, UpdateUserLockedCommand command) {
        boolean locked = Boolean.TRUE.equals(command.getLocked());

        UserEntity user = userDao.selectById(id);
        if (user == null) {
            R.error(AuthRsm.USER_NOT_FOUND);
        }
        // 封禁自己会把当前管理会话也踢掉, 通常不是有意为之; 解封自己没有这个问题
        if (locked && id.equals(StpUtil.getLoginIdAsLong())) {
            R.error(AuthRsm.CANNOT_BAN_SELF);
        }

        if (!Objects.equals(user.getLocked(), locked)) {
            user.setLocked(locked);
            if (userDao.updateById(user) == 0) {
                R.error(GenericRsm.UPDATE_FAILED);
            }
        }

        // 成功落库后再踢。已是封禁态也再踢一次, 这样管理员可以重试上一次没踢成功的会话。
        // 解封不恢复会话: 旧 token 已经被注销, 只能重新登录。
        if (locked) {
            StpUtil.kickout(id);
        }
    }

    @Override
    @CacheEvict(key = "#id")
    public void changeOwnNickname(Long id, ChangeOwnNicknameCommand command) {
        command.check();

        UserEntity user = userDao.selectById(id);
        if (user == null) {
            R.error(AuthRsm.USER_NOT_FOUND);
        }
        user.setNickname(command.getNickname());
        if (userDao.updateById(user) == 0) {
            R.error(GenericRsm.UPDATE_FAILED);
        }
    }

    /**
     * 改密码后注销该账号的全部会话。
     * <p>
     * 用 {@code logout(id)} 而不是 {@code kickout(id)}: 两者在这里效果相同(都会清掉 token),
     * 但 {@code logout} 的语义是"用户主动退出", 不会在 sa-token 日志里留下"被踢下线"的记录。
     * 当前请求已经通过鉴权, 响应仍会正常返回; 前端收到成功后清 token 回登录页。
     * <p>
     * 密码不在 {@link UserRes} 里, 所以不需要清用户缓存。
     */
    @Override
    public void changeOwnPassword(Long id, ChangeOwnPasswordCommand command) {
        UserEntity user = userDao.selectById(id);
        if (user == null) {
            R.error(AuthRsm.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(command.getOldPassword(), user.getPassword())) {
            R.error(AuthRsm.OLD_PASSWORD_ERROR);
        }
        if (Objects.equals(command.getOldPassword(), command.getNewPassword())) {
            R.error(AuthRsm.PASSWORD_UNCHANGED);
        }

        user.setPassword(passwordEncoder.encode(command.getNewPassword()));
        if (userDao.updateById(user) == 0) {
            R.error(GenericRsm.UPDATE_FAILED);
        }

        StpUtil.logout(id);
    }
}
