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

/** 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。 */
export const WORK_ORDER_PERMISSIONS = {
  list: 'GET:/api/ops/work_order',
  detail: 'GET:/api/ops/work_order/{id}',
  create: 'POST:/api/ops/work_order',
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
  createBy: ResolvedUser | null
  createTime: string | null
  aiConversationId: string | null
}

/** 详情 = 列表行 + 正文。正文单独取，见后端 WorkOrderDetailRes 的说明。 */
export interface WorkOrderDetail extends WorkOrderRow {
  problemDescription: string
  /** 用户原始描述，一行一轮 */
  originalProblemDescription: string | null
  solutionDetail: string | null
  aiSelectedOptionId: number | null
  assigneeId: number | null
  assigneeNote: string | null
}

export interface SubmitWorkOrderPayload {
  title: string
  type: string
  priority: string
  problemDescription: string
  originalProblemDescription?: string
  solutionDetail?: string
  /** AI 会话 id，归档指针。手工建单时没有 */
  aiConversationId?: string
  aiSelectedOptionId?: number
}

export interface ListWorkOrderParams {
  current: number
  size: number
  /** 按标题/描述模糊匹配 */
  keyword?: string
  status?: WorkOrderStatus
}

// ---- 接口 ----

export function listWorkOrders(params: ListWorkOrderParams): Promise<PageResult<WorkOrderRow>> {
  return request<PageResult<WorkOrderRow>>('/api/ops/work_order', {
    query: {
      current: params.current,
      size: params.size,
      keyword: params.keyword,
      status: params.status,
    },
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
