-- ============================================================
-- service-work-order 建表脚本
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
-- 工单表
--
-- 字段形状直接对齐 service-ai 生成的工单草稿 —— 标题、类型、优先级、描述一一对应。
-- 刻意保持一一对应, 中间不做任何重命名, 这样"AI 给了什么、库里存了什么"一眼能对上。
-- 工单不存解决方法: 具体怎么处理由受理人接手时决定, AI 只负责把问题整理清楚。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS work_orders CASCADE;
CREATE TABLE work_orders
(
    id                           BIGINT        NOT NULL,
    title                        VARCHAR(200)  NOT NULL,
    type                         VARCHAR(16)   NOT NULL,
    priority                     VARCHAR(16)   NOT NULL,
    problem_description          VARCHAR(2000) NOT NULL,
    /*
      用户原始描述, 一行一轮。
      用户在意图推断页可以反复补充, 所以这里存的不是"一段话"而是"一串话" ——
      每补充一次就多一行, 顺序与补充顺序一致。不为此单开一张子表: 这些行没有
      各自独立的生命周期, 从不单独查询或修改, 拆表只会多一次 join。
    */
    original_problem_description TEXT          NULL,
    /*
      是否为自助解决记录。
      true 表示用户依据 AI 给出的知识库建议自行处理完毕，建单时直接置为 RESOLVED，
      受理人为建单人。它复用普通状态机，只是多一个来源标记。
    */
    self_resolved                BOOLEAN       NOT NULL DEFAULT FALSE,
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
    ai_conversation_id           VARCHAR(36)   NULL,
    ai_selected_option_id        INTEGER       NULL,
    assignee_id                  BIGINT        NULL,
    assignee_note                VARCHAR(1000) NULL,
    /*
      取消申请时间。NULL = 没有待确认的取消申请。
      这不是一个状态值: 申请期间状态仍是 ACCEPTED, 受理人同意后状态改为 CLOSED,
      拒绝则把这个时间清空。
    */
    cancel_requested_at          TIMESTAMP     NULL,
    status                       VARCHAR(16)   NOT NULL DEFAULT 'NEW',

    create_by                    BIGINT        NULL,
    create_time                  TIMESTAMP     NULL,
    update_by                    BIGINT        NULL,
    update_time                  TIMESTAMP     NULL,
    deleted                      INTEGER       NOT NULL DEFAULT 0,
    version                      INTEGER       NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_work_orders_status ON work_orders (status);
CREATE INDEX idx_work_orders_conversation ON work_orders (ai_conversation_id);

-- "我的工单"按提交人 / 受理人过滤。已经在跑的库不要执行整份脚本(它是 drop/create), 手工补:
--     CREATE INDEX IF NOT EXISTS idx_work_orders_create_by ON work_orders (create_by);
--     CREATE INDEX IF NOT EXISTS idx_work_orders_assignee ON work_orders (assignee_id);
-- 从上一版升级（删除解决方法字段、新增自助解决标记）:
--     ALTER TABLE work_orders DROP COLUMN IF EXISTS solution_detail;
--     ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS self_resolved BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_work_orders_create_by ON work_orders (create_by);
CREATE INDEX idx_work_orders_assignee ON work_orders (assignee_id);

COMMENT ON TABLE work_orders IS '运维工单';
COMMENT ON COLUMN work_orders.id IS '主键, 雪花ID';
COMMENT ON COLUMN work_orders.title IS '工单标题';
COMMENT ON COLUMN work_orders.type IS '工单类型: DEMAND-需求 FAULT-故障';
COMMENT ON COLUMN work_orders.priority IS '优先级: LOW/MEDIUM/HIGH/URGENT';
COMMENT ON COLUMN work_orders.problem_description IS 'AI 整理后的工单描述';
COMMENT ON COLUMN work_orders.original_problem_description IS '用户原始描述, 一行一轮(补充几次就有几行)';
COMMENT ON COLUMN work_orders.self_resolved IS '是否自助解决记录: true-用户自行解决, 建单即RESOLVED';
COMMENT ON COLUMN work_orders.ai_conversation_id IS 'AI 会话ID, 指向 spring_ai_chat_memory.conversation_id';
COMMENT ON COLUMN work_orders.ai_selected_option_id IS '用户在意图推断里选中的选项 id';
COMMENT ON COLUMN work_orders.assignee_id IS '受理人ID';
COMMENT ON COLUMN work_orders.assignee_note IS '受理人结单时填写的本次心得';
COMMENT ON COLUMN work_orders.cancel_requested_at IS '取消申请时间, NULL-没有待确认的取消申请';
COMMENT ON COLUMN work_orders.status IS '状态: NEW/ACCEPTED/RESOLVED/CLOSED';
COMMENT ON COLUMN work_orders.create_by IS '创建人ID';
COMMENT ON COLUMN work_orders.create_time IS '创建时间';
COMMENT ON COLUMN work_orders.update_by IS '更新人ID';
COMMENT ON COLUMN work_orders.update_time IS '更新时间';
COMMENT ON COLUMN work_orders.deleted IS '逻辑删除: 0-未删除 1-已删除';
COMMENT ON COLUMN work_orders.version IS '乐观锁版本号';
