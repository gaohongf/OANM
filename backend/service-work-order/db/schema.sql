-- ============================================================
-- service-work-order 建表脚本
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
-- 工单表
--
-- 字段形状直接对齐 service-ai 那个"辅助填单"接口的输出 —— 模型每吐一行单字段
-- JSON, 就落一列。刻意保持一一对应, 中间不做任何重命名, 这样"AI 给了什么、
-- 库里存了什么"一眼能对上, 排查时不用在脑子里做一次映射。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `work_orders`;
CREATE TABLE `work_orders`
(
    `id`                           BIGINT        NOT NULL COMMENT '主键, 雪花ID',
    `title`                        VARCHAR(200)  NOT NULL COMMENT '工单标题',
    `type`                         VARCHAR(16)   NOT NULL COMMENT '工单类型: DEMAND-需求 FAULT-故障',
    `priority`                     VARCHAR(16)   NOT NULL COMMENT '优先级: LOW/MEDIUM/HIGH/URGENT',
    `problem_description`          VARCHAR(2000) NOT NULL COMMENT 'AI 整理后的工单描述',
    /*
      用户原始描述, 一行一轮。
      用户在意图推断页可以反复补充, 所以这里存的不是"一段话"而是"一串话" ——
      每补充一次就多一行, 顺序与补充顺序一致。不为此单开一张子表: 这些行没有
      各自独立的生命周期, 从不单独查询或修改, 拆表只会多一次 join。
    */
    `original_problem_description` TEXT          NULL COMMENT '用户原始描述, 一行一轮(补充几次就有几行)',
    `solution_detail`              TEXT          NULL COMMENT 'AI 给出的解决方法',
    /*
      AI 会话 ID, 归档指针。

      长度 36 不是随手取的: 它指向 service-ai 的 spring_ai_chat_memory 表, 那张表的
      conversation_id 就是 varchar(36)（存 ChatMemory 自动生成的 UUID）。所以拿这个值
      能把整段对话原样捞回来:

          SELECT * FROM spring_ai_chat_memory WHERE conversation_id = '<这里的值>' ORDER BY timestamp;

      注意它是**指针而不是副本**: 对话本体在 service-ai 那边, 删掉那些记忆行之后这个
      指针就悬空了。真需要"入库即不可变"的归档时, 应该另加一列存对话正文, 而不是
      指望这张表。
    */
    `ai_conversation_id`           VARCHAR(36)   NULL COMMENT 'AI 会话ID, 指向 spring_ai_chat_memory.conversation_id',
    `ai_selected_option_id`        INT           NULL COMMENT '用户在意图推断里选中的选项 id',
    `assignee_id`                  BIGINT        NULL COMMENT '受理人ID',
    `assignee_note`                VARCHAR(1000) NULL COMMENT '受理人笔记',
    `status`                       VARCHAR(16)   NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/ACCEPTED/RESOLVED/CLOSED',

    `create_by`                    BIGINT        NULL COMMENT '创建人ID',
    `create_time`                  DATETIME      NULL COMMENT '创建时间',
    `update_by`                    BIGINT        NULL COMMENT '更新人ID',
    `update_time`                  DATETIME      NULL COMMENT '更新时间',
    `deleted`                      INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `version`                      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    KEY `idx_work_orders_status` (`status`),
    KEY `idx_work_orders_conversation` (`ai_conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='运维工单';
