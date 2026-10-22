package com.github.gaohongf.auth.dao;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.UserEntity;

/**
 * 用户表 DAO
 */
@Mapper
public interface UserDao extends BaseMapper<UserEntity> {

    /**
     * 查询用户拥有的角色名列表
     *
     * @param userId 用户ID
     * @return 角色名列表, 用户不存在或无角色时返回空集合
     */
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);

    /**
     * 查询持有某角色的用户 id 列表, 供"给某个角色的全部成员发通知"使用。
     *
     * <h2>为什么这里过滤掉了已封号和已删除的用户</h2>
     * 站内信是"登录后才能看到"的东西, 而封号用户登不进来 —— 为他们的每一条通知都写一行
     * 永远没人读的数据, 只会让收件箱表白长。已删除的用户同理。
     * <p>
     * 刻意没有 join {@code roles} 再过滤一次逻辑删除: 角色被任何用户持有时不允许删除
     * （见 {@code RoleService.delete} 的说明）, 所以不存在"持有一个已删角色"的用户。
     *
     * @param roleName 角色名, 如 admin
     * @return 用户 id 列表, 去重且升序; 该角色没有成员时返回空集合
     */
    List<Long> selectUserIdsByRole(@Param("roleName") String roleName);

    /**
     * 查询持有某权限（或通配符 {@code *}）的用户 id 列表。
     * <p>
     * 用于"派发工单时的候选人"：候选人 = 能自己接单的人。已封号/已删除的用户不在其中，
     * 因为他们即使被派到也处理不了。
     *
     * @param permissionKey 权限键，如 {@code PUT:/api/ops/work_order/{id}/claim}
     * @return 用户 id 列表, 去重且升序; 没有人持有该权限时返回空集合
     */
    List<Long> selectUserIdsByPermission(@Param("permissionKey") String permissionKey);

    Optional<UserEntity> selectByUsername(@Param("username") String username);

    /**
     * 统计同名用户的行数, <b>包含逻辑删除的行</b>。
     * <p>
     * 用于创建用户前的重名检查。不能换成 {@code exists}: BaseMapper 会按 {@code @TableLogic}
     * 追加 {@code deleted = 0}, 而"用户名"上的唯一索引并不区分逻辑删除 ——
     * 一条已删除的同名墓碑行仍会让 insert 失败, 提前查出来才能给出业务提示而不是 500。
     */
    long countByUsernameIncludingDeleted(@Param("username") String username);
}
