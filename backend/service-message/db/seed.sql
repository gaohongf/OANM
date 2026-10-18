-- ============================================================
-- service-message 引导数据
-- 数据库: PostgreSQL 18
--
-- 现在只需要一条模板: 工单创建后通知超管的那条。其余模板随"谁要发通知"一起加,
-- 不预先造假模板 —— 没人用的模板行会让人以为某个功能已经接好了。
--
-- 本脚本可以重复执行（幂等）。
--
-- 关于 id 为什么是负数: 理由与 service-auth/db/seed.sql 里那段相同 ——
-- 应用侧写入的是雪花 id(永远为正), 负数 id 不可能与真实数据冲突。
-- 约定: 负数 id = 手写的内置行。
-- ============================================================

-- ------------------------------------------------------------
-- 工单创建通知(站内信)
--
-- 参数由 service-work-order 传(见 WorkOrderCreatedNotifier):
--   {title} 工单标题, {typeLabel} 工单类型的中文名(需求/故障)
--
-- 这里收的是 {typeLabel} 而不是枚举的 name(): 本仓库的约定是
-- **代码在链路上跑, 中文在展示层翻**, 而正文是给超管看的展示文本, 出现 FAULT 是代码值漏到了界面上。
-- 中文由生产方翻译好传进来, 消息模块自己不认识工单枚举 —— 那样加一种工单类型就得改消息模块的代码。
--
-- 判重按 (template_key, channel) 而不是按 id: 已经跑起来的库里这条可能是管理员
-- 手工加的(雪花 id), 而这里是保留负 id。判重键选错的话会出现两行同样的模板,
-- 消费端按唯一索引查出来的是哪一行就成了运气问题。
-- ------------------------------------------------------------
INSERT INTO message_templates (id, template_key, channel, title_template, content_template,
                               enabled, remark, create_time, update_time, deleted, version)
SELECT -1, 'work_order.created', 'APP', '新的工单',
       '工单「{title}」已提交（类型: {typeLabel}），请及时处理。',
       1, '建单成功后通知 admin 角色的全部成员', NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1
                  FROM message_templates
                  WHERE template_key = 'work_order.created'
                    AND channel = 'APP');

-- ------------------------------------------------------------
-- 上面那句 WHERE NOT EXISTS 只管"没有才插入", 对**已经跑起来**的库是空操作 ——
-- 而模板正文是会被改的(这次就改了 {type} → {typeLabel})。没有这一句的话,
-- 开发库里会一直留着旧文案, 现象是"种子里明明改对了, 通知还是旧的"。
--
-- 带上 content_template <> '新值' 是为了幂等: 第二次执行影响到 0 行。
-- 只改正文, 不动 id / title: 管理员手工改过的标题不该被脚本覆盖。
-- ------------------------------------------------------------
UPDATE message_templates
SET content_template = '工单「{title}」已提交（类型: {typeLabel}），请及时处理。',
    update_time      = NOW()
WHERE template_key = 'work_order.created'
  AND channel = 'APP'
  AND content_template <> '工单「{title}」已提交（类型: {typeLabel}），请及时处理。';
