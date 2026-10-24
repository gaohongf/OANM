-- ============================================================
-- service-software 建表脚本
-- 数据库: PostgreSQL 18
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
--   本文件是"整份重建", 含 DROP TABLE。已经在跑的库不要直接执行,
--   按下面的 CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS 手工补。
-- ============================================================

-- ------------------------------------------------------------
-- 软件分类（目前只有一级）
--
-- 名称唯一靠"部分唯一索引"实现: 逻辑删除下, 普通唯一索引会让
-- "删除分类 → 重建同名分类"直接撞键, 而那条被删的行对用户已经不可见了。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS software_versions CASCADE;
DROP TABLE IF EXISTS software CASCADE;
DROP TABLE IF EXISTS software_categories CASCADE;

CREATE TABLE software_categories
(
    id          BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    sort        INTEGER      NOT NULL DEFAULT 0,

    create_by   BIGINT       NULL,
    create_time TIMESTAMP    NULL,
    update_by   BIGINT       NULL,
    update_time TIMESTAMP    NULL,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    version     INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_software_categories_name
    ON software_categories (name) WHERE deleted = 0;

COMMENT ON TABLE software_categories IS '软件分类';
COMMENT ON COLUMN software_categories.id IS '主键, 雪花ID';
COMMENT ON COLUMN software_categories.name IS '分类名称';
COMMENT ON COLUMN software_categories.sort IS '展示顺序, 小的在前';

-- ------------------------------------------------------------
-- 软件目录项（不含具体版本）
--
-- 厂商放在这里而不是版本上: 一个产品的厂商不随版本变化。
-- 同名判断限定在同一分类内 —— "驱动"和"工具"各有一个"万能驱动"是合理的。
-- ------------------------------------------------------------
CREATE TABLE software
(
    id          BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    vendor      VARCHAR(100) NULL,
    category_id BIGINT       NOT NULL,
    description TEXT         NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,

    create_by   BIGINT       NULL,
    create_time TIMESTAMP    NULL,
    update_by   BIGINT       NULL,
    update_time TIMESTAMP    NULL,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    version     INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_software_category_id ON software (category_id);
CREATE UNIQUE INDEX uk_software_category_name
    ON software (category_id, name) WHERE deleted = 0;

COMMENT ON TABLE software IS '软件目录';
COMMENT ON COLUMN software.id IS '主键, 雪花ID';
COMMENT ON COLUMN software.name IS '软件名称, 同一分类下不重复';
COMMENT ON COLUMN software.vendor IS '厂商 / 发布方';
COMMENT ON COLUMN software.category_id IS '所属分类ID';
COMMENT ON COLUMN software.description IS '简介';
COMMENT ON COLUMN software.enabled IS '是否上架: 停用后下载页不可见';

-- ------------------------------------------------------------
-- 软件版本
--
-- 版本号列叫 version_no 而不是 version: version 已经被乐观锁占用,
-- 同名列会让 MyBatis-Plus 的更新条件与业务字段互相覆盖。
--
-- file_url 必须是 /file/ 开头的同源地址（应用层校验）;
-- download_count 只由下载接口原子自增, 管理端编辑请求永远不接收它。
-- ------------------------------------------------------------
CREATE TABLE software_versions
(
    id             BIGINT       NOT NULL,
    software_id    BIGINT       NOT NULL,
    version_no     VARCHAR(50)  NOT NULL,
    platform       VARCHAR(16)  NOT NULL,
    release_note   TEXT         NULL,
    file_id        VARCHAR(64)  NULL,
    file_url       VARCHAR(512) NOT NULL,
    file_name      VARCHAR(256) NOT NULL,
    file_size      BIGINT       NOT NULL DEFAULT 0,
    download_count BIGINT       NOT NULL DEFAULT 0,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,

    create_by      BIGINT       NULL,
    create_time    TIMESTAMP    NULL,
    update_by      BIGINT       NULL,
    update_time    TIMESTAMP    NULL,
    deleted        INTEGER      NOT NULL DEFAULT 0,
    version        INTEGER      NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_software_versions_software_id ON software_versions (software_id);

-- 同一软件下, "版本号 + 平台"唯一。同样必须是部分索引, 否则删掉旧版本后
-- 无法用相同的版本号重新发布。
CREATE UNIQUE INDEX uk_software_versions_no_platform
    ON software_versions (software_id, version_no, platform) WHERE deleted = 0;

COMMENT ON TABLE software_versions IS '软件版本';
COMMENT ON COLUMN software_versions.id IS '主键, 雪花ID';
COMMENT ON COLUMN software_versions.software_id IS '所属软件ID';
COMMENT ON COLUMN software_versions.version_no IS '业务版本号, 如 1.2.3';
COMMENT ON COLUMN software_versions.platform IS '适用平台: WINDOWS/MACOS/LINUX/COMMON';
COMMENT ON COLUMN software_versions.release_note IS '版本说明 / 更新日志';
COMMENT ON COLUMN software_versions.file_id IS 'service-files 的文件记录ID';
COMMENT ON COLUMN software_versions.file_url IS '同源访问地址, 必须以 /file/ 开头';
COMMENT ON COLUMN software_versions.file_name IS '上传时的原始文件名';
COMMENT ON COLUMN software_versions.file_size IS '文件大小(字节)';
COMMENT ON COLUMN software_versions.download_count IS '下载次数, 仅由下载接口递增';
COMMENT ON COLUMN software_versions.enabled IS '是否可下载: 停用后下载页不可见';
