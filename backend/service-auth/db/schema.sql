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

-- ------------------------------------------------------------
-- 前端菜单树
--
-- 职责: 一份定义同时喂前端菜单渲染与前端路由注册。
--   有 component = 一个可打开的页面; 没有 component = 一个只用来分组的目录。
--   刻意没有 type 列: 这个区分能从 component 推导出来, 存一份就多一处可能不一致的状态。
--   按钮级权限也不需要菜单行 —— 前端拿到的是 /api/auth/me 返回的权限键列表, 按钮直接用键判断。
--
-- component 的取值约定(跨阶段契约, 阶段四的前端按这个查组件):
--   相对 src/pages/ 的路径, 如 ops/work-order/index。
--   前端用 import.meta.glob('../pages/**/*.tsx') 建一张白名单按路径索引, 因此
--   后端只能"选一个已存在的页面", 不能加载任意模块; 查不到就渲染 404 并 WARN。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `menus`;
CREATE TABLE `menus`
(
    `id`            BIGINT       NOT NULL COMMENT '主键, 雪花ID',
    `parent_id`     BIGINT       NULL COMMENT '父节点ID; NULL 表示顶级',
    `name`          VARCHAR(64)  NOT NULL COMMENT '显示标题',
    `path`          VARCHAR(255) NULL COMMENT '前端路由, 如 /ops/work-order',
    `component`     VARCHAR(128) NULL COMMENT '前端页面标识(相对 src/pages/ 的路径); 为空即目录',
    `icon`          VARCHAR(64)  NULL COMMENT '图标名',
    `sort`          INT          NOT NULL DEFAULT 0 COMMENT '同级排序, 升序',
    `hidden`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '在侧边栏隐藏(但仍可路由, 用于详情页)',
    `keep_alive`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '前端是否缓存该页面',
    -- 用外键而不是权限键字符串: 字符串的失效模式是"权限改名后菜单关联静默断开 →
    -- 菜单失去门禁 → 对所有人可见", 是 fail-open 的安全退化。外键则改名天然安全。
    `permission_id` BIGINT       NULL COMMENT '所需权限, 关联 permissions.id; NULL 表示登录即可见',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `locked`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '人工锁定: 1-自注册不得覆盖',
    `source`        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL-人工 REGISTERED-自注册',

    `create_by`     BIGINT       NULL COMMENT '创建人ID',
    `create_time`   DATETIME     NULL COMMENT '创建时间',
    `update_by`     BIGINT       NULL COMMENT '更新人ID',
    `update_time`   DATETIME     NULL COMMENT '更新时间',
    `deleted`       INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    KEY `idx_menus_parent_id` (`parent_id`),
    KEY `idx_menus_permission_id` (`permission_id`),
    CONSTRAINT `fk_menus_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='前端菜单树';

-- ------------------------------------------------------------
-- 网关路由(只负责转发, 不负责鉴权)
--
-- 粒度是"服务前缀": 一行一个 path_pattern → uri。端点级的权限信息不在这里,
-- 而在 permissions 表里(键形如 GET:/api/ops/work_order/{id})。
--
-- 为什么刻意不把两者合到一张表: 少一行路由的表现是"网关 404", 少一条权限的表现是
-- "服务 403"。分开之后, 路由永远不会因为权限数据缺失而中断, 故障现象指向正确的方向。
--
-- 没有 method 列: 前缀与方法无关。
-- 没有 permission_id 列: 一个前缀不对应单一权限。
-- 没有 filters 列: 按路径约定(服务内路径 == 外部路径)不需要 StripPrefix,
--   现在加一个没人用的过滤器列只会逼着现在就发明一种序列化格式。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `api_routes`;
CREATE TABLE `api_routes`
(
    `id`           BIGINT       NOT NULL COMMENT '主键, 雪花ID',
    `name`         VARCHAR(64)  NOT NULL COMMENT '备注名, 如 工单服务',
    `path_pattern` VARCHAR(255) NOT NULL COMMENT '路径前缀, 如 /api/ops/**',
    `uri`          VARCHAR(255) NOT NULL COMMENT '目标服务, 如 lb://service-work-order',
    `route_order`  INT          NOT NULL DEFAULT 0 COMMENT '网关路由优先级, 越小越优先',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `locked`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '人工锁定: 1-自注册不得覆盖',
    `source`       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL-人工 REGISTERED-自注册',

    `create_by`    BIGINT       NULL COMMENT '创建人ID',
    `create_time`  DATETIME     NULL COMMENT '创建时间',
    `update_by`    BIGINT       NULL COMMENT '更新人ID',
    `update_time`  DATETIME     NULL COMMENT '更新时间',
    `deleted`      INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_routes_path_pattern` (`path_pattern`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='网关路由(只负责转发)';
