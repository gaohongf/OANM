-- ============================================================
-- service-knowledge 引导数据
-- 数据库: PostgreSQL 18 + pgvector
--
-- 只种四个分类。文章由管理页面录入, 不放种子里 ——
-- 正文需要先嵌入才能参与语义检索, 种子阶段没有 Ollama 调用。
--
-- 本脚本可重复执行:
--   负数 id 是手写内置行的约定（与 service-auth/db/seed.sql 一致）,
--   判重同时看 id 和"未删除的同名行", 避免管理员手工建过同名分类后重复插入。
-- ============================================================

INSERT INTO knowledge_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -1, '网络设备', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM knowledge_categories
                  WHERE id = -1 OR (name = '网络设备' AND deleted = 0));

INSERT INTO knowledge_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -2, '服务器与系统', 2, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM knowledge_categories
                  WHERE id = -2 OR (name = '服务器与系统' AND deleted = 0));

INSERT INTO knowledge_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -3, '数据库与中间件', 3, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM knowledge_categories
                  WHERE id = -3 OR (name = '数据库与中间件' AND deleted = 0));

INSERT INTO knowledge_categories (id, name, sort, create_time, update_time, deleted, version)
SELECT -4, '办公终端', 4, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM knowledge_categories
                  WHERE id = -4 OR (name = '办公终端' AND deleted = 0));
