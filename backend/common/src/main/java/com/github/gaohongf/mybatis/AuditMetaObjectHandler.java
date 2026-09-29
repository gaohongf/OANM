package com.github.gaohongf.mybatis;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 公共字段自动填充, 对应 {@link com.github.gaohongf.model.BaseEntity}。
 * <p>
 * 填充规则:
 * <ul>
 *   <li>createTime / updateTime: 由本处理器取当前时间</li>
 *   <li>createBy / updateBy: 取 sa-token 当前登录用户 ID, 取不到则保持为 null</li>
 *   <li>deleted: 插入时置 0 (未删除)</li>
 *   <li>version: 插入时置 0 (乐观锁起始版本)</li>
 * </ul>
 * <p>
 * 说明: 登录态取自 sa-token 的当前请求上下文, 因此非 Web 线程 (定时任务、MQ 消费者等)
 * 或未登录的请求下取不到操作人, 此时对应列为 null (库中可空), 不会抛异常。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentOperatorId();

        // 以下字段在 BaseEntity 上标注了 @TableField(fill = ...), 可以用严格填充。
        // 严格填充在值为 null 时会自动跳过, 所以 operatorId 为空时不会写入脏数据。
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", Long.class, operatorId);
        strictInsertFill(metaObject, "updateBy", Long.class, operatorId);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);

        // version 只标了 @Version, 没有 @TableField(fill = ...), 严格填充会跳过它,
        // 所以这里用非严格的 setFieldValByName 手动兜底。两列在库中都是 NOT NULL, 必须给出值。
        setFieldValByName("version", 0, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // updateTime / updateBy 标注的是 FieldFill.INSERT_UPDATE, 更新时同样适用
        Long operatorId = currentOperatorId();
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", Long.class, operatorId);
    }

    /**
     * 取当前登录用户 ID。
     * <p>
     * 捕获的是 RuntimeException 而不是 NotLoginException: 除了「未登录」, 无 Web 上下文时
     * sa-token 抛的是 SaTokenContextException, 登录 ID 非数字时抛的是 NumberFormatException,
     * 这几种情况的对策一致 —— 视为取不到操作人。
     *
     * <p><b>为什么是 public static</b>: 批量插入用的自定义 XML（{@code RolePermissionDao.insertBatch}、
     * {@code UserRoleDao.insertBatch}）是裸 SQL, 不经过 MyBatis-Plus 的插入路径, 本处理器的
     * 自动填充对它们<b>不生效</b>。那些地方必须自己填审计字段, 所以需要复用这段
     * "当前操作人怎么取"的逻辑, 而不是各写一份（否则 sa-token 的取值方式一改就会漏改）。
     *
     * @return 当前登录用户 ID, 取不到时返回 null
     */
    public static Long currentOperatorId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
