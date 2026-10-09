-- ============================================================
-- service-auth 引导数据
-- 数据库: MySQL 8.x
--
-- 用途: 解决"第一个管理员怎么进去"这个鸡生蛋问题 ——
--       要管菜单/路由/角色, 你得先有 POST:/api/auth/menus 这类权限;
--       要有这个权限, 得先有人给你授权。
--
-- 本脚本可以重复执行（幂等）。刻意只做三件事:
--   1. 建 admin 角色
--   2. 建一个 key='*' 的权限（超级管理员, 匹配一切权限）
--   3. 把 '*' 授给 admin
--
-- 刻意<b>不做</b>"启动时自动把全部权限授给 admin": 那样每次重启都会把管理员
-- 故意回收掉的权限重新写回去, 是静默提权。自动建权限行倒是安全（幂等、无副作用）,
-- 但那属于自注册的范畴, 由对应的阶段负责。
--
-- 关于 id 为什么是负数:
--   permissions.id 平时由应用侧用雪花算法赋值, 雪花 id 永远是正数。
--   所以负数 id 不可能和任何应用写入的行冲突 —— 而这正是种子数据最怕的事:
--   随便挑一个正数 id, 一旦撞上已有行, ON DUPLICATE KEY 会把那一行的
--   permission_key 改掉, 等于毁掉真实数据。
--   约定: 负数 id = 手写的内置行。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. admin 角色
--    roles 的主键是 role_name(String), 没有独立 id。
-- ------------------------------------------------------------
INSERT INTO roles (role_name, label, create_time, update_time, deleted, version)
SELECT 'admin', '管理员', NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'admin');

-- ------------------------------------------------------------
-- 2. 超级管理员权限
--
--    key 就是单个 '*', 它能匹配一切请求, 原因见 sa-token 的匹配语义:
--    用户的权限列表是"模式"列表, 传入的请求是"目标";
--    SaFoxUtil.vagueMatch 里 '*' 匹配任意长度(含 0)的字符。
--    所以 hasPermission("GET:/api/ops/work_order/{id}") 在持有 '*' 时即为 true。
--
--    这也是为什么管理端各端点<b>不需要</b>预先有各自的权限行: 拦截器检查的是
--    "用户自己的权限列表能否匹配本次请求", 持有 '*' 就匹配一切。
--    那些端点各自的权限行只在需要给非管理员角色做细粒度授权时才有用。
-- ------------------------------------------------------------
INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -1, '*', '全部权限（超级管理员）', NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = '*');

-- ------------------------------------------------------------
-- 3. 把 '*' 授给 admin
-- ------------------------------------------------------------
INSERT INTO role_permissions (role_name, permission_id, create_time, update_time, deleted, version)
SELECT 'admin', -1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM role_permissions rp
                           INNER JOIN permissions p ON p.id = rp.permission_id
                  WHERE rp.role_name = 'admin'
                    AND p.permission_key = '*'
                    AND rp.deleted = 0);

-- ------------------------------------------------------------
-- 4. 初始菜单树
--
--    为什么必须有这一段: 菜单是**唯一**的入口配置 —— 没有菜单, 管理员登录后看到的是
--    "还没有可访问的功能", 而菜单管理本身也需要一条菜单才能进得去。这是个鸡生蛋,
--    只能靠种子数据打开。
--
--    这一段是给全新安装用的。如果你已经手工建过菜单树, 跳过它。
--    幂等性靠负数的保留 id 保证(见文件头部关于负数 id 的说明), 重复执行会被跳过。
--
--    component 的取值必须与前端 src/pages/ 下的文件路径**逐字一致(大小写敏感)**,
--    对不上时前端会渲染"页面未注册"而不是白屏。
-- ------------------------------------------------------------

-- 4.1 菜单要挂的权限。先补权限行, 再建菜单 ——
--     否则菜单的 permission_id 子查询会取到 NULL, 菜单会变成"登录即可见"(静默失去门禁)。
INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -2, 'GET:/api/ops/work_order/{id}', '查看工单', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/ops/work_order/{id}');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -3, 'GET:/api/auth/menus', '查看菜单', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/auth/menus');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -4, 'GET:/api/auth/roles', '查看角色', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/auth/roles');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -5, 'GET:/api/auth/users', '查看用户', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/auth/users');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -6, 'GET:/api/auth/permissions', '查看权限', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/auth/permissions');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -7, 'GET:/api/auth/api-routes', '查看路由', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/auth/api-routes');

-- 4.2 顶级节点
INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -10, NULL, '概览', '/dashboard', 'Dashboard', 'dashboard', 1, 0, 0, NULL, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -10);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -20, NULL, '运维管理', NULL, NULL, 'setting', 10, 0, 0, NULL, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -20);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -30, NULL, '系统管理', NULL, NULL, 'team', 20, 0, 0, NULL, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -30);

-- 4.3 子节点。permission_id 按 key 现查, 这样无论权限行是种子建的(负 id)
--     还是后来手工/自注册建的(雪花 id), 都能挂对。
INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -21, -20, '工单管理', '/ops/work-order', 'ops/WorkOrder', 'file', 1, 0, 1,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/ops/work_order/{id}'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -21);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -31, -30, '菜单管理', '/system/menus', 'system/MenuList', 'appstore', 1, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/auth/menus'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -31);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -32, -30, '角色管理', '/system/roles', 'system/RoleList', 'user', 2, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/auth/roles'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -32);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -33, -30, '用户管理', '/system/users', 'system/UserList', 'user', 3, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/auth/users'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -33);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -34, -30, '权限管理', '/system/permissions', 'system/PermissionList', 'api', 4, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/auth/permissions'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -34);

-- 路由管理用 setting 图标（与"运维管理"目录同名图标，但它们在树的不同分支，不会混淆）
INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -35, -30, '路由管理', '/system/routes', 'system/ApiRouteList', 'setting', 5, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/auth/api-routes'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -35);

-- ------------------------------------------------------------
-- 5. 把第一个用户挂上 admin —— 无法预知是哪个用户, 所以只能人工执行一次。
--    把下面的 <用户ID> 换成一个真实存在的 users.id 后再去掉注释执行。
--    users.id 可以从 service-auth 的库里查: SELECT id, username FROM users;
--
--    挂上之后, 该用户<b>重新登录</b>即可获得 '*' 权限, 从此能在界面上做后续所有配置。
-- ------------------------------------------------------------
-- INSERT INTO user_roles (user_id, role_name, create_time, update_time, deleted, version)
-- VALUES (<用户ID>, 'admin', NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 6. AI 工单助手（意图推断 + 辅助填单）
--
--    这两个页面是 SSE 流式接口的界面, 和其它页面一样需要菜单 + 权限才能进去。
-- ------------------------------------------------------------

-- 6.1 页面要挂的权限。
--     这两个键 service-ai 启动时会自注册出来, 那为什么还要在这里种一遍?
--     因为种子脚本可能比服务先跑 —— 那时权限行还不存在, 6.2 的子查询就会取到 NULL,
--     而 NULL 的 permission_id 意味着"登录即可见", 菜单会静默失去门禁(fail-open)。
--     这里的 NOT EXISTS 保证了"谁先建都只有一行", 服务后来自注册时也会跳过。
INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -8, 'GET:/api/ai/assistant/wo/uiii', '意图推断', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/ai/assistant/wo/uiii');

INSERT INTO permissions (id, permission_key, label, create_time, update_time, deleted, version)
SELECT -9, 'GET:/api/ai/assistant/wo/assist-submit', 'AI 辅助填单', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE permission_key = 'GET:/api/ai/assistant/wo/assist-submit');

-- 6.2 菜单。component 必须与前端 src/pages/ 下的文件路径逐字一致(大小写敏感):
--     src/pages/ops/IntentInference.tsx → 'ops/IntentInference'
--     src/pages/ops/AssistSubmit.tsx    → 'ops/AssistSubmit'
INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -22, -20, '智能提单', '/ops/ai/intent', 'ops/IntentInference', 'robot', 2, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/ai/assistant/wo/uiii'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -22);

INSERT INTO menus (id, parent_id, name, path, component, icon, sort, hidden, keep_alive, permission_id, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -23, -20, 'AI 辅助填单', '/ops/ai/submit', 'ops/AssistSubmit', 'robot', 3, 0, 0,
       (SELECT id FROM permissions WHERE permission_key = 'GET:/api/ai/assistant/wo/assist-submit'),
       1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM menus WHERE id = -23);

-- 6.3 网关路由。
--
--     按 path_pattern 判重而不是按 id: 已经跑起来的库里, 这几行是管理员在
--     路由管理界面手工建的(雪花 id), 而这里是保留负 id。判重键选错的话,
--     同一条前缀会出现两行, 网关拿到两条同序路由 —— 表现是随机的 404。
--
--     route_order 沿用现有库里的取值(ops 10 / ai 11 / auth 21): 数值越小越优先,
--     彼此不重叠, 所以顺序只影响日志可读性, 但保持一致能避免"种子装的和我现在跑的
--     不一样"这种难查的差异。
INSERT INTO api_routes (id, name, path_pattern, uri, route_order, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -40, 'Work Order Service', '/api/ops/**', 'lb://service-work-order', 10, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM api_routes WHERE path_pattern = '/api/ops/**');

INSERT INTO api_routes (id, name, path_pattern, uri, route_order, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -41, 'AI Service', '/api/ai/**', 'lb://service-ai', 11, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM api_routes WHERE path_pattern = '/api/ai/**');

INSERT INTO api_routes (id, name, path_pattern, uri, route_order, enabled, locked, source, create_time, update_time, deleted, version)
SELECT -42, 'Auth Service', '/api/auth/**', 'lb://service-auth', 21, 1, 0, 'MANUAL', NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM api_routes WHERE path_pattern = '/api/auth/**');

-- ------------------------------------------------------------
-- 7. 一次性修复: 删除两个已经不存在端点的权限行
--
--    service-ai 的两个端点现在挂在 /api/ai/assistant/wo/ 下, 之前是没有 /wo 这一段
--    的旧路径(wouiii / wos)。自注册是"只增不删"的, 所以改动路径之后, 旧路径的权限行
--    留在了库里 —— 它们永远不会再被匹配上, 但仍然<b>出现在权限管理界面里、仍然可以被授予</b>,
--    授了也没有任何效果。
--
--    物理删除而不是置 deleted=1: permissions.permission_key 上有唯一键,
--    墓碑行会让这个 key 日后重新注册时直接撞唯一键。
-- ------------------------------------------------------------
DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions
                        WHERE permission_key IN ('GET:/api/ai/assistant/wouiii',
                                                 'GET:/api/ai/assistant/wos'));

DELETE FROM permissions
WHERE permission_key IN ('GET:/api/ai/assistant/wouiii',
                         'GET:/api/ai/assistant/wos');
