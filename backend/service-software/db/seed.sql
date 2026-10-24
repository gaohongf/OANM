-- ============================================================
-- service-software 引导数据
-- 数据库: PostgreSQL 18
--
-- 只种三个一级分类。软件本身和版本由管理页面录入, 不放在种子里 ——
-- 文件地址一旦写死, 换一台机器就会指向不存在的文件。
--
-- 本脚本可重复执行:
--   负数 id 是手写内置行的约定（与 service-auth/db/seed.sql 一致）,
--   判重同时看 id 和"未删除的同名行", 避免管理员手工建过同名分类后重复插入。
-- ============================================================

INSERT INTO software_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -1, '打印机驱动', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM software_categories
                  WHERE id = -1 OR (name = '打印机驱动' AND deleted = 0));

INSERT INTO software_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -2, '办公工具', 2, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM software_categories
                  WHERE id = -2 OR (name = '办公工具' AND deleted = 0));

INSERT INTO software_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -3, '企业软件', 3, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM software_categories
                  WHERE id = -3 OR (name = '企业软件' AND deleted = 0));
