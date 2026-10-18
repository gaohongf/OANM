-- ============================================================
-- service-message 建表脚本
-- 数据库: PostgreSQL 18
--
-- 说明:
--   1. 所有表统一继承 BaseEntity, 公共字段为:
--      create_by / create_time / update_by / update_time / deleted / version
--   2. deleted 为 MyBatis-Plus 逻辑删除字段 (0-未删除 1-已删除)
--   3. version 为 MyBatis-Plus 乐观锁字段, 更新时必须携带该字段
--   4. 主键 id 使用雪花算法生成 (IdType.ASSIGN_ID), 由应用侧赋值
--   (以上约定与 service-auth/db/schema.sql 一致)
-- ============================================================

-- ------------------------------------------------------------
-- 站内信
--
-- 这张表是"消息"的唯一权威来源: 推给前端的 SSE 只是"更快知道", 未读数、列表、
-- 已读状态全部以这里为准。所以 SSE 断了、丢了、跨实例没命中, 都不影响一致性 ——
-- 客户端下一次轮询就能对齐。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS notifications CASCADE;
CREATE TABLE notifications
(
    id         BIGINT        NOT NULL,
    /*
      幂等键, 由生产方生成, 要求是**确定性**的值(如 work_order.created:123:7)。

      Kafka 是至少一次投递: 消费位点提交与落库之间崩溃就会重投, 所以去重只能靠库。
      用唯一索引而不是"先查后插": 两者并发时都会漏(查的时候还没有, 插的时候已经有了),
      而唯一索引让数据库来做这件事, 重投进来是一条 DuplicateKeyException, 消费端当作正常情况忽略。

      注意 deleted 有意**不**参与这个唯一索引: 逻辑删除后墓碑行仍然占着这个 event_id,
      于是同一条消息重新投递时会被唯一索引挡住。这正是想要的 —— 已读/删除不影响幂等性。
    */
    event_id   VARCHAR(128)  NOT NULL,
    receiver   BIGINT        NOT NULL,
    sender     BIGINT        NULL,
    channel    VARCHAR(16)   NOT NULL,
    type       VARCHAR(64)   NOT NULL,
    /*
      标题与正文是**渲染结果的快照**, 不是模板引用。

      这样改一次模板措辞不会让用户已经收到的历史消息跟着变样(否则"我明明看到的是 A,
      现在怎么成了 B"是个无法自证的问题), 也让"消息列表"这个查询完全不依赖模板表。
      代价是同一句话在库里存了很多份 —— 站内信量级下这个代价可以忽略。
    */
    title      VARCHAR(200)  NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    biz_id     VARCHAR(64)   NULL,
    read_flag  INTEGER       NOT NULL DEFAULT 0,
    read_time  TIMESTAMP     NULL,

    create_by  BIGINT        NULL,
    create_time TIMESTAMP    NULL,
    update_by  BIGINT        NULL,
    update_time TIMESTAMP    NULL,
    deleted    INTEGER       NOT NULL DEFAULT 0,
    version    INTEGER       NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_event_id UNIQUE (event_id)
);

/*
  两个列表索引, 而不是一个。

  收件箱有两个查询: "全部"(只看 receiver) 和 "未读"(receiver + read_flag),
  两边都按 id DESC 翻页。而 (receiver, read_flag, id) 这个索引**排不了"全部"**:
  它按 read_flag 分段, 段内才按 id 有序, 于是"全部"的翻页要么走文件排序,
  要么回表后再过滤。所以"全部"单独给一个 (receiver, id)。
*/
CREATE INDEX idx_notifications_unread ON notifications (receiver, read_flag, id);
CREATE INDEX idx_notifications_receiver ON notifications (receiver, id);

COMMENT ON TABLE notifications IS '站内信';
COMMENT ON COLUMN notifications.id IS '主键, 雪花ID';
COMMENT ON COLUMN notifications.event_id IS '幂等键, 生产方生成的确定性值';
COMMENT ON COLUMN notifications.receiver IS '收件人ID';
COMMENT ON COLUMN notifications.sender IS '触发人ID, 系统消息为 NULL';
COMMENT ON COLUMN notifications.channel IS '渠道: APP-站内信 EMAIL-邮件';
COMMENT ON COLUMN notifications.type IS '模板键, 对应 message_templates.template_key';
COMMENT ON COLUMN notifications.title IS '标题(渲染快照)';
COMMENT ON COLUMN notifications.content IS '正文(渲染快照)';
COMMENT ON COLUMN notifications.biz_id IS '业务ID(如工单id), 前端点击消息后跳转用';
COMMENT ON COLUMN notifications.read_flag IS '已读: 0-未读 1-已读';
COMMENT ON COLUMN notifications.read_time IS '已读时间';
COMMENT ON COLUMN notifications.create_by IS '创建人ID';
COMMENT ON COLUMN notifications.create_time IS '创建时间';
COMMENT ON COLUMN notifications.update_by IS '更新人ID';
COMMENT ON COLUMN notifications.update_time IS '更新时间';
COMMENT ON COLUMN notifications.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN notifications.version IS '乐观锁版本号';

-- ------------------------------------------------------------
-- 消息模板
--
-- 生产方发的是"模板键 + 参数"(见 MessageCreateCommand), 措辞在这里统一维护 ——
-- 改一次文案不用改 N 个服务并重新发版, 这是模板化唯一的理由。
--
-- 没有"模板版本"列: 历史消息存的是渲染快照, 改模板不影响已发出的消息,
-- 于是"这条消息当初用的是哪版模板"只在排查渲染问题时才有意义, 而那种问题
-- 靠的是当时的错误日志(渲染失败不落库), 不是一条版本号。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS message_templates CASCADE;
CREATE TABLE message_templates
(
    id                BIGINT        NOT NULL,
    template_key      VARCHAR(64)   NOT NULL,
    channel           VARCHAR(16)   NOT NULL,
    /*
      占位符用 {名字} 语法, 由 TemplateRenderer 渲染(不是 MessageFormat, 理由见那个类)。
      两个模板都必须写: 站内信的标题可以是固定文案, 但"不填标题"和"标题渲染成空串"
      在库里是不同的东西, 后者说明模板写错了。
    */
    title_template    VARCHAR(200)  NOT NULL,
    content_template  VARCHAR(2000) NOT NULL,
    enabled           INTEGER       NOT NULL DEFAULT 1,
    remark            VARCHAR(200)  NULL,

    create_by         BIGINT        NULL,
    create_time       TIMESTAMP     NULL,
    update_by         BIGINT        NULL,
    update_time       TIMESTAMP     NULL,
    deleted           INTEGER       NOT NULL DEFAULT 0,
    version           INTEGER       NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    /*
      键 + 渠道唯一, 而不是键唯一: 同一件事的站内信和邮件本来就该是两套措辞
      (站内信可以只写标题, 邮件必须有称呼和落款)。将来接入 EMAIL 时不需要动表。
    */
    CONSTRAINT uk_message_templates_key_channel UNIQUE (template_key, channel)
);

COMMENT ON TABLE message_templates IS '消息模板';
COMMENT ON COLUMN message_templates.id IS '主键, 雪花ID';
COMMENT ON COLUMN message_templates.template_key IS '模板键, 生产方按它指定用哪套模板';
COMMENT ON COLUMN message_templates.channel IS '渠道: APP-站内信 EMAIL-邮件';
COMMENT ON COLUMN message_templates.title_template IS '标题模板, {占位符} 语法';
COMMENT ON COLUMN message_templates.content_template IS '正文模板, {占位符} 语法';
COMMENT ON COLUMN message_templates.enabled IS '是否启用: 0-停用 1-启用';
COMMENT ON COLUMN message_templates.remark IS '备注, 说明这条模板给谁用';
COMMENT ON COLUMN message_templates.create_by IS '创建人ID';
COMMENT ON COLUMN message_templates.create_time IS '创建时间';
COMMENT ON COLUMN message_templates.update_by IS '更新人ID';
COMMENT ON COLUMN message_templates.update_time IS '更新时间';
COMMENT ON COLUMN message_templates.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN message_templates.version IS '乐观锁版本号';
