-- ============================================================
-- Docker Compose 专用：初始化第一个管理员。
--
-- 项目没有注册入口，seed.sql 也不种用户（"第一个管理员"只能人工指定），
-- 一键启动如果还要先去数据库里插一行才能登录，就不算一键了。
-- 这里放一个仅用于本地/演示环境的默认管理员：
--
--     用户名: admin
--     密码:   admin123
--
-- 登录后请立即在「系统管理 → 用户管理」里修改密码。
-- 生产部署不要使用本脚本（把 docker-compose.yml 里这个挂载去掉，
-- 按 README 的手工初始化步骤创建管理员）。
--
-- 密码用 pgcrypto 的 bcrypt 生成，与后端 SecurityConfig 的
-- BCryptPasswordEncoder 兼容；cost 固定为 10。
-- 本脚本在数据库初始化时只执行一次（initdb 只对空数据目录运行）。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, username, nickname, locked, password,
                   create_by, create_time, update_by, update_time, deleted, version)
SELECT -100,
       'admin',
       '管理员',
       FALSE,
       crypt('admin123', gen_salt('bf', 10)),
       NULL, NOW(), NULL, NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- admin 角色由 service-auth 的 seed.sql（02 号脚本）创建
INSERT INTO user_roles (user_id, role_name, create_by, create_time, update_by, update_time, deleted, version)
SELECT u.id, 'admin', NULL, NOW(), NULL, NOW(), 0, 0
FROM users u
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_name = 'admin'
  );
