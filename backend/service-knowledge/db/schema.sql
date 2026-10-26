-- ============================================================
-- service-knowledge 建表脚本
-- 数据库: PostgreSQL 18 + pgvector
--
-- 说明:
--   1. 所有表统一继承 BaseEntity, 公共字段为:
--      create_by / create_time / update_by / update_time / deleted / version
--   2. deleted 为 MyBatis-Plus 逻辑删除字段 (0-未删除 1-已删除)
--   3. version 为 MyBatis-Plus 乐观锁字段, 更新时必须携带该字段
--   4. 主键 id 使用雪花算法生成 (IdType.ASSIGN_ID), 由应用侧赋值
--   (以上约定与 service-auth/db/schema.sql 一致)
--
-- 注意:
--   1. 本文件是"整份重建", 含 DROP TABLE。已经在跑的库不要直接执行,
--      按下面的 CREATE TABLE / CREATE INDEX 手工补。
--   2. CREATE EXTENSION vector 需要数据库超级用户或已授予的权限;
--      普通 postgres 镜像没有这个扩展, 请用 pgvector/pgvector:pg18。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------
-- 知识库分类（目前只有一级）
--
-- 名称唯一用部分唯一索引: 逻辑删除下普通唯一索引会让
-- "删除分类 → 重建同名分类"直接撞键。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS knowledge_chunks CASCADE;
DROP TABLE IF EXISTS knowledge_article_revisions CASCADE;
DROP TABLE IF EXISTS knowledge_articles CASCADE;
DROP TABLE IF EXISTS knowledge_categories CASCADE;

CREATE TABLE knowledge_categories
(
    id          BIGINT      NOT NULL,
    name        VARCHAR(64) NOT NULL,
    sort        INTEGER     NOT NULL DEFAULT 0,

    create_by   BIGINT      NULL,
    create_time TIMESTAMP   NULL,
    update_by   BIGINT      NULL,
    update_time TIMESTAMP   NULL,
    deleted     INTEGER     NOT NULL DEFAULT 0,
    version     INTEGER     NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_knowledge_categories_name
    ON knowledge_categories (name) WHERE deleted = 0;

COMMENT ON TABLE knowledge_categories IS '知识库分类';
COMMENT ON COLUMN knowledge_categories.name IS '分类名称, 未删除的行内唯一';
COMMENT ON COLUMN knowledge_categories.sort IS '展示顺序, 小的在前';

-- ------------------------------------------------------------
-- 知识库文章
--
-- status 与 index_status 分开:
--   status       是业务状态 (草稿/已发布), 决定文章是否可见;
--   index_status 是派生状态 (未索引/索引中/就绪/失败), 只影响语义检索。
-- 索引失败时文章仍然可以按关键字搜到, 所以两者不能合并。
--
-- content 是 Markdown 原文, 前端用 react-markdown 渲染。
-- ------------------------------------------------------------
CREATE TABLE knowledge_articles
(
    id           BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    summary      VARCHAR(500) NULL,
    content      TEXT         NOT NULL,
    category_id  BIGINT       NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    index_status VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    index_error  VARCHAR(500) NULL,
    indexed_at   TIMESTAMP    NULL,
    publish_time TIMESTAMP    NULL,
    view_count   BIGINT       NOT NULL DEFAULT 0,

    create_by    BIGINT       NULL,
    create_time  TIMESTAMP    NULL,
    update_by    BIGINT       NULL,
    update_time  TIMESTAMP    NULL,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    version      INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_knowledge_articles_category ON knowledge_articles (category_id);
CREATE INDEX idx_knowledge_articles_status ON knowledge_articles (status);

-- 同一分类下标题不能重复。必须是部分索引: 删掉旧文章后要能用同一个标题重建。
CREATE UNIQUE INDEX uk_knowledge_articles_title
    ON knowledge_articles (category_id, title) WHERE deleted = 0;

COMMENT ON TABLE knowledge_articles IS '知识库文章';
COMMENT ON COLUMN knowledge_articles.title IS '标题, 同一分类下不重复';
COMMENT ON COLUMN knowledge_articles.summary IS '摘要, 列表页展示';
COMMENT ON COLUMN knowledge_articles.content IS 'Markdown 正文';
COMMENT ON COLUMN knowledge_articles.category_id IS '所属分类ID';
COMMENT ON COLUMN knowledge_articles.status IS '业务状态: DRAFT/PUBLISHED';
COMMENT ON COLUMN knowledge_articles.index_status IS '索引状态: NONE/INDEXING/READY/FAILED';
COMMENT ON COLUMN knowledge_articles.index_error IS '最近一次索引失败的原因';
COMMENT ON COLUMN knowledge_articles.indexed_at IS '最近一次索引成功的时间';
COMMENT ON COLUMN knowledge_articles.publish_time IS '首次发布时间';
COMMENT ON COLUMN knowledge_articles.view_count IS '浏览次数, 仅由详情接口原子递增';

-- ------------------------------------------------------------
-- 文章修订快照
--
-- 每次保存都追加一条完整快照, 因此"当前文章 == revision_no 最大的那一条"。
-- 不回填历史行的 deleted/version: 修订历史只增不改。
--
-- 快照不存 status: 回滚只恢复正文相关内容, 不恢复发布状态 ——
-- 否则回滚到某个草稿时期的版本会把已发布文章悄悄下架, 或者反过来。
-- ------------------------------------------------------------
CREATE TABLE knowledge_article_revisions
(
    id                 BIGINT       NOT NULL,
    article_id         BIGINT       NOT NULL,
    revision_no        INTEGER      NOT NULL,
    title              VARCHAR(200) NOT NULL,
    summary            VARCHAR(500) NULL,
    content            TEXT         NOT NULL,
    category_id        BIGINT       NOT NULL,
    change_type        VARCHAR(16)  NOT NULL,
    source_revision_id BIGINT       NULL,

    create_by          BIGINT       NULL,
    create_time        TIMESTAMP    NULL,
    update_by          BIGINT       NULL,
    update_time        TIMESTAMP    NULL,
    deleted            INTEGER      NOT NULL DEFAULT 0,
    version            INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_knowledge_revisions_article ON knowledge_article_revisions (article_id);
CREATE UNIQUE INDEX uk_knowledge_revisions_no
    ON knowledge_article_revisions (article_id, revision_no) WHERE deleted = 0;

COMMENT ON TABLE knowledge_article_revisions IS '知识库文章修订快照';
COMMENT ON COLUMN knowledge_article_revisions.revision_no IS '同一文章内从 1 递增';
COMMENT ON COLUMN knowledge_article_revisions.change_type IS 'CREATE/UPDATE/ROLLBACK';
COMMENT ON COLUMN knowledge_article_revisions.source_revision_id IS '回滚来源的修订ID';

-- ------------------------------------------------------------
-- 文章分块与向量（派生数据, 随时可重建）
--
-- embedding 是 vector(2000), 必须与
-- spring.ai.ollama.embedding.options.dimensions 一致。
--
-- 这张表不走 MyBatis-Plus:
--   1. vector 列没有现成的类型处理器, 读写必须用 PGobject;
--   2. 重建索引时按 article_id 直接物理删除 —— 逻辑删除会在 HNSW 索引里
--      留下墓碑行, 越积越慢。deleted 列只为与 BaseEntity 的字段风格一致,
--      永远写 0。
-- ------------------------------------------------------------
CREATE TABLE knowledge_chunks
(
    id          BIGINT    NOT NULL,
    article_id  BIGINT    NOT NULL,
    chunk_index INTEGER   NOT NULL,
    content     TEXT      NOT NULL,
    embedding   vector(2000) NOT NULL,

    create_by   BIGINT    NULL,
    create_time TIMESTAMP NULL,
    update_by   BIGINT    NULL,
    update_time TIMESTAMP NULL,
    deleted     INTEGER   NOT NULL DEFAULT 0,
    version     INTEGER   NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_knowledge_chunks_article_chunk
    ON knowledge_chunks (article_id, chunk_index) WHERE deleted = 0;

-- 余弦距离。HNSW 对 vector 类型的上限就是 2000 维。
CREATE INDEX idx_knowledge_chunks_hnsw
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

COMMENT ON TABLE knowledge_chunks IS '知识库文章分块与嵌入向量（派生数据）';
COMMENT ON COLUMN knowledge_chunks.chunk_index IS '同一文章内从 0 递增';
COMMENT ON COLUMN knowledge_chunks.content IS '分块原文（不含前置标题）';
COMMENT ON COLUMN knowledge_chunks.embedding IS 'qwen3-embedding 向量, 2000 维';
