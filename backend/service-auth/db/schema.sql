-- ============================================================
-- service-auth 认证授权模块 建表脚本
-- 数据库: PostgreSQL 18
-- 说明:
--   1. 所有表统一继承 BaseEntity, 公共字段为:
--      create_by / create_time / update_by / update_time / deleted / version
--   2. deleted 为 MyBatis-Plus 逻辑删除字段 (0-未删除 1-已删除)
--   3. version 为 MyBatis-Plus 乐观锁字段, 更新时必须携带该字段
--   4. 主键 id 使用雪花算法生成 (IdType.ASSIGN_ID), 由应用侧赋值
--
-- 方言说明:
--   - 标识符统一小写、不加引号; 布尔列用 BOOLEAN, 时间列用 TIMESTAMP
--   - PostgreSQL 的外键会阻止删除被引用的表, 所以 DROP 一律带 CASCADE
--   - 索引名在同一个 schema 内必须全局唯一, 因此都带表名前缀
-- ============================================================

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users
(
    id          BIGINT      NOT NULL,
    username    VARCHAR(64) NOT NULL,
    nickname    VARCHAR(64) NOT NULL DEFAULT '',
    locked      BOOLEAN     NOT NULL DEFAULT FALSE,
    password    VARCHAR(64) NOT NULL,
    create_by   BIGINT      NULL,
    create_time TIMESTAMP   NULL,
    update_by   BIGINT      NULL,
    update_time TIMESTAMP   NULL,
    deleted     INTEGER     NOT NULL DEFAULT 0,
    version     INTEGER     NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '主键, 雪花ID';
COMMENT ON COLUMN users.username IS '用户名, 唯一';
COMMENT ON COLUMN users.nickname IS '昵称, 可重复';
COMMENT ON COLUMN users.locked IS '是否封号: FALSE-正常 TRUE-已封号';
COMMENT ON COLUMN users.password IS '密码';
COMMENT ON COLUMN users.create_by IS '创建人ID';
COMMENT ON COLUMN users.create_time IS '创建时间';
COMMENT ON COLUMN users.update_by IS '更新人ID';
COMMENT ON COLUMN users.update_time IS '更新时间';
COMMENT ON COLUMN users.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN users.version IS '乐观锁版本号';

-- ------------------------------------------------------------
-- 角色表
-- role_name 直接作为主键 (String 类型, 如 admin / user)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS roles CASCADE;
CREATE TABLE roles
(
    role_name   VARCHAR(64) NOT NULL,
    label       VARCHAR(64) NULL,

    create_by   BIGINT      NULL,
    create_time TIMESTAMP   NULL,
    update_by   BIGINT      NULL,
    update_time TIMESTAMP   NULL,
    deleted     INTEGER     NOT NULL DEFAULT 0,
    version     INTEGER     NOT NULL DEFAULT 0,

    PRIMARY KEY (role_name)
);

COMMENT ON TABLE roles IS '角色表';
COMMENT ON COLUMN roles.role_name IS '角色名, 唯一, 如 admin / user';
COMMENT ON COLUMN roles.label IS '角色标签, 如 管理员 / 用户';
COMMENT ON COLUMN roles.create_by IS '创建人ID';
COMMENT ON COLUMN roles.create_time IS '创建时间';
COMMENT ON COLUMN roles.update_by IS '更新人ID';
COMMENT ON COLUMN roles.update_time IS '更新时间';
COMMENT ON COLUMN roles.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN roles.version IS '乐观锁版本号';

-- ------------------------------------------------------------
-- 权限表
-- id 为内部雪花主键, permission_key 为对外业务标识, 避免直接泄露主键
-- ------------------------------------------------------------
DROP TABLE IF EXISTS permissions CASCADE;
CREATE TABLE permissions
(
    id             BIGINT       NOT NULL,
    permission_key VARCHAR(255) NOT NULL,
    label          VARCHAR(64)  NULL,

    create_by      BIGINT       NULL,
    create_time    TIMESTAMP    NULL,
    update_by      BIGINT       NULL,
    update_time    TIMESTAMP    NULL,
    deleted        INTEGER      NOT NULL DEFAULT 0,
    version        INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_permission_key UNIQUE (permission_key)
);

COMMENT ON TABLE permissions IS '权限表';
COMMENT ON COLUMN permissions.id IS '主键, 雪花ID';
COMMENT ON COLUMN permissions.permission_key IS '权限标识, 唯一, 如 order:read, GET:/users/{id}';
COMMENT ON COLUMN permissions.label IS '权限标签, 如 查看工单';
COMMENT ON COLUMN permissions.create_by IS '创建人ID';
COMMENT ON COLUMN permissions.create_time IS '创建时间';
COMMENT ON COLUMN permissions.update_by IS '更新人ID';
COMMENT ON COLUMN permissions.update_time IS '更新时间';
COMMENT ON COLUMN permissions.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN permissions.version IS '乐观锁版本号';

-- ------------------------------------------------------------
-- 用户-角色关联表
-- 无独立主键, 使用 (user_id, role_name) 联合主键保证不重复
-- ------------------------------------------------------------
DROP TABLE IF EXISTS user_roles CASCADE;
CREATE TABLE user_roles
(
    user_id     BIGINT      NOT NULL,
    role_name   VARCHAR(64) NOT NULL,

    create_by   BIGINT      NULL,
    create_time TIMESTAMP   NULL,
    update_by   BIGINT      NULL,
    update_time TIMESTAMP   NULL,
    deleted     INTEGER     NOT NULL DEFAULT 0,
    version     INTEGER     NOT NULL DEFAULT 0,

    PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_name) REFERENCES roles (role_name)
);

CREATE INDEX idx_user_roles_role_name ON user_roles (role_name);

COMMENT ON TABLE user_roles IS '用户-角色关联表';
COMMENT ON COLUMN user_roles.user_id IS '用户ID, 关联 users.id';
COMMENT ON COLUMN user_roles.role_name IS '角色名, 关联 roles.role_name';
COMMENT ON COLUMN user_roles.create_by IS '创建人ID';
COMMENT ON COLUMN user_roles.create_time IS '创建时间';
COMMENT ON COLUMN user_roles.update_by IS '更新人ID';
COMMENT ON COLUMN user_roles.update_time IS '更新时间';
COMMENT ON COLUMN user_roles.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN user_roles.version IS '乐观锁版本号';

-- ------------------------------------------------------------
-- 角色-权限关联表
-- 无独立主键, 使用 (role_name, permission_id) 联合主键保证不重复
-- ------------------------------------------------------------
DROP TABLE IF EXISTS role_permissions CASCADE;
CREATE TABLE role_permissions
(
    role_name     VARCHAR(64) NOT NULL,
    permission_id BIGINT      NOT NULL,

    create_by     BIGINT      NULL,
    create_time   TIMESTAMP   NULL,
    update_by     BIGINT      NULL,
    update_time   TIMESTAMP   NULL,
    deleted       INTEGER     NOT NULL DEFAULT 0,
    version       INTEGER     NOT NULL DEFAULT 0,

    PRIMARY KEY (role_name, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_name) REFERENCES roles (role_name),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);

COMMENT ON TABLE role_permissions IS '角色-权限关联表';
COMMENT ON COLUMN role_permissions.role_name IS '角色名, 关联 roles.role_name';
COMMENT ON COLUMN role_permissions.permission_id IS '权限ID, 关联 permissions.id';
COMMENT ON COLUMN role_permissions.create_by IS '创建人ID';
COMMENT ON COLUMN role_permissions.create_time IS '创建时间';
COMMENT ON COLUMN role_permissions.update_by IS '更新人ID';
COMMENT ON COLUMN role_permissions.update_time IS '更新时间';
COMMENT ON COLUMN role_permissions.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN role_permissions.version IS '乐观锁版本号';

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
DROP TABLE IF EXISTS menus CASCADE;
CREATE TABLE menus
(
    id            BIGINT       NOT NULL,
    parent_id     BIGINT       NULL,
    name          VARCHAR(64)  NOT NULL,
    path          VARCHAR(255) NULL,
    component     VARCHAR(128) NULL,
    icon          VARCHAR(64)  NULL,
    sort          INTEGER      NOT NULL DEFAULT 0,
    hidden        BOOLEAN      NOT NULL DEFAULT FALSE,
    keep_alive    BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 用外键而不是权限键字符串: 字符串的失效模式是"权限改名后菜单关联静默断开 →
    -- 菜单失去门禁 → 对所有人可见", 是 fail-open 的安全退化。外键则改名天然安全。
    permission_id BIGINT       NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    source        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',

    create_by     BIGINT       NULL,
    create_time   TIMESTAMP    NULL,
    update_by     BIGINT       NULL,
    update_time   TIMESTAMP    NULL,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    version       INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT fk_menus_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE INDEX idx_menus_parent_id ON menus (parent_id);
CREATE INDEX idx_menus_permission_id ON menus (permission_id);

COMMENT ON TABLE menus IS '前端菜单树';
COMMENT ON COLUMN menus.id IS '主键, 雪花ID';
COMMENT ON COLUMN menus.parent_id IS '父节点ID; NULL 表示顶级';
COMMENT ON COLUMN menus.name IS '显示标题';
COMMENT ON COLUMN menus.path IS '前端路由, 如 /ops/work-order';
COMMENT ON COLUMN menus.component IS '前端页面标识(相对 src/pages/ 的路径); 为空即目录';
COMMENT ON COLUMN menus.icon IS '图标名';
COMMENT ON COLUMN menus.sort IS '同级排序, 升序';
COMMENT ON COLUMN menus.hidden IS '在侧边栏隐藏(但仍可路由, 用于详情页)';
COMMENT ON COLUMN menus.keep_alive IS '前端是否缓存该页面';
COMMENT ON COLUMN menus.permission_id IS '所需权限, 关联 permissions.id; NULL 表示登录即可见';
COMMENT ON COLUMN menus.enabled IS '是否启用';
COMMENT ON COLUMN menus.locked IS '人工锁定: TRUE-自注册不得覆盖';
COMMENT ON COLUMN menus.source IS '来源: MANUAL-人工 REGISTERED-自注册';
COMMENT ON COLUMN menus.create_by IS '创建人ID';
COMMENT ON COLUMN menus.create_time IS '创建时间';
COMMENT ON COLUMN menus.update_by IS '更新人ID';
COMMENT ON COLUMN menus.update_time IS '更新时间';
COMMENT ON COLUMN menus.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN menus.version IS '乐观锁版本号';

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
DROP TABLE IF EXISTS api_routes CASCADE;
CREATE TABLE api_routes
(
    id           BIGINT       NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    uri          VARCHAR(255) NOT NULL,
    route_order  INTEGER      NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    source       VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',

    create_by    BIGINT       NULL,
    create_time  TIMESTAMP    NULL,
    update_by    BIGINT       NULL,
    update_time  TIMESTAMP    NULL,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    version      INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_api_routes_path_pattern UNIQUE (path_pattern)
);

COMMENT ON TABLE api_routes IS '网关路由(只负责转发)';
COMMENT ON COLUMN api_routes.id IS '主键, 雪花ID';
COMMENT ON COLUMN api_routes.name IS '备注名, 如 工单服务';
COMMENT ON COLUMN api_routes.path_pattern IS '路径前缀, 如 /api/ops/**';
COMMENT ON COLUMN api_routes.uri IS '目标服务, 如 lb://service-work-order';
COMMENT ON COLUMN api_routes.route_order IS '网关路由优先级, 越小越优先';
COMMENT ON COLUMN api_routes.enabled IS '是否启用';
COMMENT ON COLUMN api_routes.locked IS '人工锁定: TRUE-自注册不得覆盖';
COMMENT ON COLUMN api_routes.source IS '来源: MANUAL-人工 REGISTERED-自注册';
COMMENT ON COLUMN api_routes.create_by IS '创建人ID';
COMMENT ON COLUMN api_routes.create_time IS '创建时间';
COMMENT ON COLUMN api_routes.update_by IS '更新人ID';
COMMENT ON COLUMN api_routes.update_time IS '更新时间';
COMMENT ON COLUMN api_routes.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN api_routes.version IS '乐观锁版本号';
