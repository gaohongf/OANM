-- ============================================================
-- service-auth 认证授权模块 建表脚本
-- 数据库: MySQL 8.x
-- 说明:
--   1. 所有表统一继承 BaseEntity, 公共字段为:
--      create_by / create_time / update_by / update_time / deleted / version
--   2. deleted 为 MyBatis-Plus 逻辑删除字段 (0-未删除 1-已删除)
--   3. version 为 MyBatis-Plus 乐观锁字段, 更新时必须携带该字段
--   4. 主键 id 使用雪花算法生成 (IdType.ASSIGN_ID), 由应用侧赋值
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`
(
    `id`          BIGINT       NOT NULL COMMENT '主键, 雪花ID',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名, 唯一',
    `nickname`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称, 可重复',
    `locked`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否封号: 0-正常 1-已封号',
    `password`    VARCHAR(64)  NOT NULL COMMENT '密码',
    `create_by`   BIGINT       NULL COMMENT '创建人ID',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   BIGINT       NULL COMMENT '更新人ID',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    `deleted`     INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ------------------------------------------------------------
-- 角色表
-- role_name 直接作为主键 (String 类型, 如 admin / user)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`
(
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名, 唯一, 如 admin / user',
    `label`       VARCHAR(64) NULL COMMENT '角色标签, 如 管理员 / 用户',

    `create_by`   BIGINT      NULL COMMENT '创建人ID',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_by`   BIGINT      NULL COMMENT '更新人ID',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    `deleted`     INT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`role_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

-- ------------------------------------------------------------
-- 权限表
-- id 为内部雪花主键, permission_key 为对外业务标识, 避免直接泄露主键
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions`
(
    `id`             BIGINT      NOT NULL COMMENT '主键, 雪花ID',
    `permission_key` VARCHAR(255) NOT NULL COMMENT '权限标识, 唯一, 如 order:read, GET:/users/{id}',
    `label`          VARCHAR(64) NULL COMMENT '权限标签, 如 查看工单',

    `create_by`     BIGINT      NULL COMMENT '创建人ID',
    `create_time`   DATETIME    NULL COMMENT '创建时间',
    `update_by`     BIGINT      NULL COMMENT '更新人ID',
    `update_time`   DATETIME    NULL COMMENT '更新时间',
    `deleted`       INT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`       INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permissions_permission_key` (`permission_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限表';

-- ------------------------------------------------------------
-- 用户-角色关联表
-- 无独立主键, 使用 (user_id, role_name) 联合主键保证不重复
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles`
(
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID, 关联 users.id',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名, 关联 roles.role_name',

    `create_by`   BIGINT      NULL COMMENT '创建人ID',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_by`   BIGINT      NULL COMMENT '更新人ID',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    `deleted`     INT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`user_id`, `role_name`),
    KEY `idx_user_roles_role_name` (`role_name`),
    CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_name`) REFERENCES `roles` (`role_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户-角色关联表';

-- ------------------------------------------------------------
-- 角色-权限关联表
-- 无独立主键, 使用 (role_name, permission_id) 联合主键保证不重复
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions`
(
    `role_name`     VARCHAR(64) NOT NULL COMMENT '角色名, 关联 roles.role_name',
    `permission_id` BIGINT      NOT NULL COMMENT '权限ID, 关联 permissions.id',

    `create_by`     BIGINT      NULL COMMENT '创建人ID',
    `create_time`   DATETIME    NULL COMMENT '创建时间',
    `update_by`     BIGINT      NULL COMMENT '更新人ID',
    `update_time`   DATETIME    NULL COMMENT '更新时间',
    `deleted`       INT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`       INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`role_name`, `permission_id`),
    KEY `idx_role_permissions_permission_id` (`permission_id`),
    CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_name`) REFERENCES `roles` (`role_name`),
    CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色-权限关联表';
