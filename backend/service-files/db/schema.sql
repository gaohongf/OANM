-- ============================================================
-- service-files 建表脚本
-- 数据库: PostgreSQL 18
--
-- file_detail 是 X File Storage 的上传记录表, 由 FileRecorderImpl 读写。
-- 表结构对齐那个库的 FileInfo: 少存一个字段, 下载/删除时就少一份信息来源;
-- 多存一个库里没有的字段, 插入会直接失败。
--
-- 注意: 这张表不继承 BaseEntity, 没有 deleted / version / update_* 列,
-- 因此 FileDetailEntity 也不继承 BaseEntity。
-- ============================================================

DROP TABLE IF EXISTS file_detail CASCADE;
CREATE TABLE file_detail
(
    id                VARCHAR(32)  NOT NULL,
    url               VARCHAR(512) NOT NULL,
    size              BIGINT       NOT NULL,
    filename          VARCHAR(256) NOT NULL,
    original_filename VARCHAR(256) NOT NULL,
    base_path         VARCHAR(256) NOT NULL,
    path              VARCHAR(256) NOT NULL,
    ext               VARCHAR(32)  NULL,
    content_type      VARCHAR(128) NULL,
    platform          VARCHAR(32)  NOT NULL,
    th_url            VARCHAR(512) NULL,
    th_filename       VARCHAR(256) NULL,
    th_size           BIGINT       NULL,
    th_content_type   VARCHAR(128) NULL,
    object_id         VARCHAR(32)  NULL,
    object_type       VARCHAR(32)  NULL,
    attr              JSONB        NULL,
    create_time       TIMESTAMPTZ  NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- 记录的所有读写都以 url 为条件(getByUrl / update / delete), 没有索引会全表扫描
CREATE INDEX idx_file_detail_url ON file_detail (url);

COMMENT ON TABLE file_detail IS '文件上传记录(X File Storage)';
COMMENT ON COLUMN file_detail.id IS '记录ID, 32 位 UUID';
COMMENT ON COLUMN file_detail.url IS '文件访问地址';
COMMENT ON COLUMN file_detail.size IS '文件大小, 单位字节';
COMMENT ON COLUMN file_detail.filename IS '存储文件名';
COMMENT ON COLUMN file_detail.original_filename IS '用户上传时的原始文件名';
COMMENT ON COLUMN file_detail.base_path IS '基础路径';
COMMENT ON COLUMN file_detail.path IS '相对路径';
COMMENT ON COLUMN file_detail.ext IS '文件扩展名';
COMMENT ON COLUMN file_detail.content_type IS 'MIME 类型';
COMMENT ON COLUMN file_detail.platform IS '存储平台标识';
COMMENT ON COLUMN file_detail.th_url IS '缩略图访问地址';
COMMENT ON COLUMN file_detail.th_filename IS '缩略图文件名';
COMMENT ON COLUMN file_detail.th_size IS '缩略图大小';
COMMENT ON COLUMN file_detail.th_content_type IS '缩略图 MIME 类型';
COMMENT ON COLUMN file_detail.object_id IS '对象存储的 objectId';
COMMENT ON COLUMN file_detail.object_type IS '对象存储的 objectType';
COMMENT ON COLUMN file_detail.attr IS '附加属性';
COMMENT ON COLUMN file_detail.create_time IS '记录创建时间';
