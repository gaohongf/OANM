import { request } from './http'
import type { PageResult, ResolvedUser } from '../types/api'

/**
 * 工单接口、类型与枚举元数据。
 *
 * ## 为什么标签映射写在这一层
 * `DEMAND → 需求` 不是样式，是**与后端的契约**：后端把枚举存成 `name()`、接口也吐
 * `name()`，前端负责在展示时翻成中文。这个翻译表被列表页和填单页同时需要，
 * 放在某一个页面里会让另一个页面复制一份，然后两处慢慢漂移。
 *
 * ## 为什么不用 TS 的 `enum`
 * `tsconfig.app.json` 开了 `erasableSyntaxOnly`，TS 的 `enum` 会生成运行时代码，
 * 直接编译不过。所以枚举一律用字符串联合类型 + 元数据表表达。
 */

export type WorkOrderType = 'DEMAND' | 'FAULT'

export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export type WorkOrderStatus = 'NEW' | 'ACCEPTED' | 'RESOLVED' | 'CLOSED'

/**
 * 「我的工单」的列表范围。
 *
 * `created` = 我提交的（按 create_by），`assigned` = 我受理的（按 assignee_id）。
 * 后端从会话里取当前登录人，前端只传范围，不传用户 id。
 */
export type WorkOrderScope = 'created' | 'assigned'

/** 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。 */
export const WORK_ORDER_PERMISSIONS = {
  list: 'GET:/api/ops/work_order',
  detail: 'GET:/api/ops/work_order/{id}',
  create: 'POST:/api/ops/work_order',
  claim: 'PUT:/api/ops/work_order/{id}/claim',
  assign: 'PUT:/api/ops/work_order/{id}/assignee',
  /** 结单。权限之外还要求操作人是当前受理人，由后端校验 */
  resolve: 'PUT:/api/ops/work_order/{id}/resolve',
  /** 取消。权限之外还要求操作人是提单人，由后端校验 */
  cancel: 'PUT:/api/ops/work_order/{id}/cancel',
  /** 受理人同意/拒绝取消申请 */
  cancelDecision: 'PUT:/api/ops/work_order/{id}/cancel/approval',
  /** 概览统计。与列表分开授权：能看汇总不等于能看每一单 */
  stats: 'GET:/api/ops/work_order/stats',
} as const

export interface EnumOption<T extends string> {
  value: T
  label: string
  /** antd Tag 的颜色。放在这里而不是页面里，是为了让同一个枚举在各处长得一样。 */
  color: string
}

export const TYPE_OPTIONS: readonly EnumOption<WorkOrderType>[] = [
  { value: 'DEMAND', label: '需求', color: 'blue' },
  { value: 'FAULT', label: '故障', color: 'red' },
]

export const PRIORITY_OPTIONS: readonly EnumOption<WorkOrderPriority>[] = [
  { value: 'LOW', label: '低', color: 'default' },
  { value: 'MEDIUM', label: '中', color: 'blue' },
  { value: 'HIGH', label: '高', color: 'orange' },
  { value: 'URGENT', label: '紧急', color: 'red' },
]

export const STATUS_OPTIONS: readonly EnumOption<WorkOrderStatus>[] = [
  { value: 'NEW', label: '待受理', color: 'default' },
  { value: 'ACCEPTED', label: '已受理', color: 'processing' },
  { value: 'RESOLVED', label: '已解决', color: 'success' },
  { value: 'CLOSED', label: '已关闭', color: 'default' },
]

// ---- 元数据查询 ----

/**
 * 按值取元数据。
 *
 * 返回 undefined 而不是兜底成一个默认标签：如果后端哪天多了一个枚举值，
 * 界面应该把**原始值**如实显出来（调用方的责任），而不是伪装成"需求"。
 */
export function findOption<T extends string>(
  options: readonly EnumOption<T>[],
  value: string | null | undefined,
): EnumOption<T> | undefined {
  if (!value) {
    return undefined
  }
  return options.find((option) => option.value === value)
}

// ---- 模型输出的容错 ----

/**
 * 把模型输出的类型值归一化成枚举。
 *
 * 提示词里已经写死了"只能是 demand 或 fault"，但模型仍可能回中文或大小写不一。
 * 后端也做了同样的宽松解析（`WorkOrderType.parse`）—— 前端先归一化一次，
 * 是为了让表单里的下拉框能选中正确的项，而不是把非法值原样提交上去换一个 400。
 *
 * @returns 归一化失败返回 null，由调用方决定是让用户手选还是提示
 */
export function normalizeType(raw: string | null | undefined): WorkOrderType | null {
  if (!raw) {
    return null
  }
  const text = raw.trim()
  const upper = text.toUpperCase()
  if (upper === 'DEMAND' || text === '需求' || text === '新需求' || text === '功能需求') {
    return 'DEMAND'
  }
  if (upper === 'FAULT' || text === '故障' || text === '问题' || text === '报错' || text === '异常') {
    return 'FAULT'
  }
  return null
}

const PRIORITY_ALIASES: Record<string, WorkOrderPriority> = {
  LOW: 'LOW',
  低: 'LOW',
  低优先级: 'LOW',
  MEDIUM: 'MEDIUM',
  中: 'MEDIUM',
  一般: 'MEDIUM',
  普通: 'MEDIUM',
  中优先级: 'MEDIUM',
  HIGH: 'HIGH',
  高: 'HIGH',
  高优先级: 'HIGH',
  重要: 'HIGH',
  URGENT: 'URGENT',
  紧急: 'URGENT',
  特急: 'URGENT',
  严重: 'URGENT',
  最高: 'URGENT',
}

/** 同 {@link normalizeType}。别名表与后端 `WorkOrderPriority.parse` 保持一致。 */
export function normalizePriority(raw: string | null | undefined): WorkOrderPriority | null {
  if (!raw) {
    return null
  }
  return PRIORITY_ALIASES[raw.trim().toUpperCase()] ?? PRIORITY_ALIASES[raw.trim()] ?? null
}

// ---- 响应类型 ----

export interface WorkOrderRow {
  /** 字符串形式的雪花 id —— 后端刻意不用数字，见 WorkOrderRes 的说明 */
  id: string
  title: string
  type: WorkOrderType
  priority: WorkOrderPriority
  status: WorkOrderStatus
  /**
   * 是否自助解决记录。
   *
   * 这类记录创建时就是 `RESOLVED`、受理人是本人，统计也按 `RESOLVED` 算；
   * 列表在状态旁标出来，不需要为它新增一个状态值。
   */
  selfResolved: boolean
  /** 同 `assigneeId`：解析失败时会退化成裸数字 */
  createBy: ResolvedUser | number | null
  /**
   * 受理人。未派发时为 null。
   *
   * 类型里的 `number` 是后端 `@User` 解析失败时的降级形态（输出裸雪花 id）：
   * 那时候拿不到用户名，只能显示 id，见 `UserCell`。
   */
  assigneeId: ResolvedUser | number | null
  createTime: string | null
  /**
   * 取消申请时间。非 null 表示工单已被受理、提单人申请取消，等待受理人确认。
   * 状态此时仍是 `ACCEPTED`，所以待确认标记要看这个字段，不能只看 status。
   */
  cancelRequestedAt: string | null
  aiConversationId: string | null
}

/** 详情 = 列表行 + 正文。正文单独取，见后端 WorkOrderDetailRes 的说明。 */
export interface WorkOrderDetail extends WorkOrderRow {
  problemDescription: string
  /** 用户原始描述，一行一轮 */
  originalProblemDescription: string | null
  /** 用户在意图推断里选中的选项；手工建单和自助解决记录为 null */
  aiSelectedOptionId: number | null
  assigneeNote: string | null
}

export interface SubmitWorkOrderPayload {
  title: string
  type: string
  priority: string
  problemDescription: string
  originalProblemDescription?: string
  /** true = 自助解决记录：服务端直接置为已解决、受理人是本人，不发建单通知 */
  selfResolved?: boolean
  /** AI 会话 id，归档指针。手工建单时没有 */
  aiConversationId?: string
  aiSelectedOptionId?: number
}

/** 概览趋势里的一天。后端已经把没有数据的日子补成 0 */
export interface DailyWorkOrderCount {
  /** YYYY-MM-DD */
  date: string
  created: number
  completed: number
}

/**
 * 概览的"最近工单动态"。
 *
 * 只有状态与标题，没有操作人 —— 后端没有操作日志，这一条反映的是
 * "这张单最近变成了什么状态"，不要把它写成"某人做了某事"。
 */
export interface RecentWorkOrder {
  id: string
  title: string
  type: WorkOrderType
  status: WorkOrderStatus
  updateTime: string | null
}

/**
 * 概览统计。
 *
 * `resolved` 由「结单」写入；`closed` 目前还没有流程写入，通常是 0。
 * `todayCompleted` 和 `daily[].completed` 是"已解决/已关闭单据的 update_time
 * 落在该时间窗内"的近似值。界面如实展示，不补假数据。
 */
export interface WorkOrderStats {
  total: number
  pending: number
  accepted: number
  resolved: number
  closed: number
  weekCreated: number
  lastWeekCreated: number
  todayCompleted: number
  yesterdayCompleted: number
  daily: DailyWorkOrderCount[]
  recent: RecentWorkOrder[]
}

export interface ListWorkOrderParams {
  current: number
  size: number
  /** 按标题/描述模糊匹配 */
  keyword?: string
  status?: WorkOrderStatus
  /** 只看当前登录人的某类工单。用户 id 由后端从会话里取，前端不传 */
  scope?: WorkOrderScope
}

// ---- 接口 ----

export function listWorkOrders(params: ListWorkOrderParams): Promise<PageResult<WorkOrderRow>> {
  return request<PageResult<WorkOrderRow>>('/api/ops/work_order', {
    query: {
      current: params.current,
      size: params.size,
      keyword: params.keyword,
      status: params.status,
      scope: params.scope,
    },
  })
}

/**
 * 概览统计。
 *
 * `days` 只有 7 / 30 两个档位；后端对其它值按 7 天处理，前端不传第三种。
 */
export function fetchWorkOrderStats(days: 7 | 30): Promise<WorkOrderStats> {
  return request<WorkOrderStats>('/api/ops/work_order/stats', { query: { days } })
}

/** 主动接取。当前登录人由后端从会话里取，前端不传。 */
export function claimWorkOrder(id: string): Promise<void> {
  return request<void>(`/api/ops/work_order/${id}/claim`, { method: 'PUT' })
}

/**
 * 派发 / 改派。
 *
 * `assigneeId` 必须是字符串：它是雪花 id，转成 number 会变成另一个用户。
 */
export function assignWorkOrder(id: string, assigneeId: string): Promise<void> {
  return request<void>(`/api/ops/work_order/${id}/assignee`, {
    method: 'PUT',
    body: { assigneeId },
  })
}

/**
 * 结单。只有当前受理人可以调用，状态会变为 `RESOLVED`。
 *
 * `note` 是选填的本次心得。没填时发送空对象，后端把 `null` 当作“没有心得”。
 */
export function resolveWorkOrder(id: string, note?: string): Promise<void> {
  const text = note?.trim()
  return request<void>(`/api/ops/work_order/${id}/resolve`, {
    method: 'PUT',
    body: text ? { note: text } : {},
  })
}

/**
 * 取消工单，只能由提单人调用。
 *
 * 没人受理时后端直接把状态改为 `CLOSED`；已受理时只写入取消申请并通知受理人。前端不需要
 * 告诉后端走哪条路 —— 状态和受理人由后端读，避免前端判断与服务端规则漂移。
 */
export function cancelWorkOrder(id: string): Promise<void> {
  return request<void>(`/api/ops/work_order/${id}/cancel`, { method: 'PUT' })
}

/**
 * 受理人处理取消申请。
 *
 * `approved=true` 同意并把工单状态改为 `CLOSED`；`false` 拒绝并清空申请。
 */
export function decideCancelWorkOrder(id: string, approved: boolean): Promise<void> {
  return request<void>(`/api/ops/work_order/${id}/cancel/approval`, {
    method: 'PUT',
    body: { approved },
  })
}

export function getWorkOrder(id: string): Promise<WorkOrderDetail> {
  return request<WorkOrderDetail>(`/api/ops/work_order/${id}`)
}

export function createWorkOrder(payload: SubmitWorkOrderPayload): Promise<{ id: string }> {
  return request<{ id: string }>('/api/ops/work_order', { method: 'POST', body: payload })
}

// ---- 小工具 ----

/**
 * 把后端的时间字段格式化成 `YYYY-MM-DD HH:mm:ss`。
 *
 * `unknown` 入参 + 两种分支是因为**这个契约没被验证过**：Spring Boot 默认关闭
 * `WRITE_DATES_AS_TIMESTAMPS`，`LocalDateTime` 出来应该是 ISO-8601 字符串；
 * 但如果有谁在配置里打开了那个开关，Jackson 会改吐 `[2026,10,8,21,34,33]` 数组。
 * 与其在界面上显示一个 `NaN`，不如两种都认。
 */
export function formatDateTime(raw: unknown): string {
  if (Array.isArray(raw)) {
    const [year, month, day, hour, minute, second] = raw as number[]
    if (typeof year === 'number') {
      const pad = (value: number | undefined) => String(value ?? 0).padStart(2, '0')
      return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
    }
    return '-'
  }
  if (typeof raw === 'string' && raw !== '') {
    // ISO-8601 的 T 换成空格更好读；截到秒，丢掉小数秒
    return raw.replace('T', ' ').slice(0, 19)
  }
  return '-'
}

/**
 * 把"一行一轮"的原始描述拆成轮次数组。
 *
 * 和写入时的约定配套：表单里它是一段多行文本，一行 = 用户补充的一轮。
 * 只拆不裁 —— 用户在表单里手改出来的空行在提交前由后端归一化。
 */
export function splitRounds(text: string | null | undefined): string[] {
  if (!text) {
    return []
  }
  return text.split('\n')
}
