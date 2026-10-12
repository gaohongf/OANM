-- ============================================================
-- service-message 建表脚本
-- 数据库: MySQL 8.x
--
-- 说明:
--   1. 所有表统一继承 BaseEntity, 公共字段为:
--      create_by / create_time / update_by / update_time / deleted / version
--   2. deleted 为 MyBatis-Plus 逻辑删除字段 (0-未删除 1-已删除)
--   3. version 为 MyBatis-Plus 乐观锁字段, 更新时必须携带该字段
--   4. 主键 id 使用雪花算法生成 (IdType.ASSIGN_ID), 由应用侧赋值
--   (以上约定与 service-auth/db/schema.sql 一致)
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 站内信
--
-- 这张表是"消息"的唯一权威来源: 推给前端的 SSE 只是"更快知道", 未读数、列表、
-- 已读状态全部以这里为准。所以 SSE 断了、丢了、跨实例没命中, 都不影响一致性 ——
-- 客户端下一次轮询就能对齐。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications`
(
    `id`         BIGINT        NOT NULL COMMENT '主键, 雪花ID',
    /*
      幂等键, 由生产方生成, 要求是**确定性**的值(如 work_order.created:123:7)。

      Kafka 是至少一次投递: 消费位点提交与落库之间崩溃就会重投, 所以去重只能靠库。
      用唯一索引而不是"先查后插": 两者并发时都会漏(查的时候还没有, 插的时候已经有了),
      而唯一索引让数据库来做这件事, 重投进来是一条 DuplicateKeyException, 消费端当作正常情况忽略。

      注意 deleted 有意**不**参与这个唯一索引: 逻辑删除后墓碑行仍然占着这个 event_id,
      于是同一条消息重新投递时会被唯一索引挡住。这正是想要的 —— 已读/删除不影响幂等性。
    */
    `event_id`   VARCHAR(128)  NOT NULL COMMENT '幂等键, 生产方生成的确定性值',
    `receiver`   BIGINT        NOT NULL COMMENT '收件人ID',
    `sender`     BIGINT        NULL COMMENT '触发人ID, 系统消息为 NULL',
    `channel`    VARCHAR(16)   NOT NULL COMMENT '渠道: APP-站内信 EMAIL-邮件',
    `type`       VARCHAR(64)   NOT NULL COMMENT '模板键, 对应 message_templates.template_key',
    /*
      标题与正文是**渲染结果的快照**, 不是模板引用。

      这样改一次模板措辞不会让用户已经收到的历史消息跟着变样(否则"我明明看到的是 A,
      现在怎么成了 B"是个无法自证的问题), 也让"消息列表"这个查询完全不依赖模板表。
      代价是同一句话在库里存了很多份 —— 站内信量级下这个代价可以忽略。
    */
    `title`      VARCHAR(200)  NOT NULL COMMENT '标题(渲染快照)',
    `content`    VARCHAR(2000) NOT NULL COMMENT '正文(渲染快照)',
    `biz_id`     VARCHAR(64)   NULL COMMENT '业务ID(如工单id), 前端点击消息后跳转用',
    `read_flag`  INT           NOT NULL DEFAULT 0 COMMENT '已读: 0-未读 1-已读',
    `read_time`  DATETIME      NULL COMMENT '已读时间',

    `create_by`  BIGINT        NULL COMMENT '创建人ID',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`  BIGINT        NULL COMMENT '更新人ID',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    `deleted`    INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`    INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notifications_event_id` (`event_id`),
    /*
      两个列表索引, 而不是一个。

      收件箱有两个查询: "全部"(只看 receiver) 和 "未读"(receiver + read_flag),
      两边都按 id DESC 翻页。而 (receiver, read_flag, id) 这个索引**排不了"全部"**:
      它按 read_flag 分段, 段内才按 id 有序, 于是"全部"的翻页要么走文件排序,
      要么回表后再过滤。所以"全部"单独给一个 (receiver, id)。
    */
    KEY `idx_notifications_unread` (`receiver`, `read_flag`, `id`),
    KEY `idx_notifications_receiver` (`receiver`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';

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
DROP TABLE IF EXISTS `message_templates`;
CREATE TABLE `message_templates`
(
    `id`                BIGINT        NOT NULL COMMENT '主键, 雪花ID',
    `template_key`      VARCHAR(64)   NOT NULL COMMENT '模板键, 生产方按它指定用哪套模板',
    `channel`           VARCHAR(16)   NOT NULL COMMENT '渠道: APP-站内信 EMAIL-邮件',
    /*
      占位符用 {名字} 语法, 由 TemplateRenderer 渲染(不是 MessageFormat, 理由见那个类)。
      两个模板都必须写: 站内信的标题可以是固定文案, 但"不填标题"和"标题渲染成空串"
      在库里是不同的东西, 后者说明模板写错了。
    */
    `title_template`    VARCHAR(200)  NOT NULL COMMENT '标题模板, {占位符} 语法',
    `content_template`  VARCHAR(2000) NOT NULL COMMENT '正文模板, {占位符} 语法',
    `enabled`           INT           NOT NULL DEFAULT 1 COMMENT '是否启用: 0-停用 1-启用',
    `remark`            VARCHAR(200)  NULL COMMENT '备注, 说明这条模板给谁用',

    `create_by`         BIGINT        NULL COMMENT '创建人ID',
    `create_time`       DATETIME      NULL COMMENT '创建时间',
    `update_by`         BIGINT        NULL COMMENT '更新人ID',
    `update_time`       DATETIME      NULL COMMENT '更新时间',
    `deleted`           INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    /*
      键 + 渠道唯一, 而不是键唯一: 同一件事的站内信和邮件本来就该是两套措辞
      (站内信可以只写标题, 邮件必须有称呼和落款)。将来接入 EMAIL 时不需要动表。
    */
    UNIQUE KEY `uk_message_templates_key_channel` (`template_key`, `channel`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='消息模板';
