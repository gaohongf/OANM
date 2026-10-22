import { openSse } from './sse'
import { request } from './http'
import type { PageResult } from '../types/api'

/**
 * 站内信接口。
 *
 * ## 后端把端点做成了"登录即可读"
 * 收件箱不需要额外权限（见后端 `NotificationController` 的类注释），所以这里没有
 * 权限键常量、页面上也没有 `<Auth>` 包裹 —— 这不是漏了。
 *
 * ## 收件人由后端从登录态取
 * 所有接口都不带 receiver 参数：后端一律用当前登录用户去查，换个 id 也读不到别人的。
 * 所以这一层不需要（也不允许）暴露"查某个用户的消息"这种能力。
 */

const BASE = '/api/message/notifications'

/** 收件箱筛选。取值与后端 `NotificationReadState` 逐字一致（大写）。 */
export type NotificationReadState = 'ALL' | 'UNREAD' | 'READ'

/**
 * 一条站内信。**列表接口与 SSE 推送共用这个形状**（后端也是同一个 `NotificationRes`），
 * 所以它同时是"列表的一行"和"推送的一帧"，不需要为推送写另一套字段。
 */
export interface NotificationItem {
  /**
   * 字符串形式的雪花 id。后端刻意不用数字 —— JS 的 Number 精确到 2^53 就到底了，
   * 而它正是前端列表去重的键：丢精度会让两条不同的消息看起来是同一条。
   */
  id: string
  /** 模板键（如 `work_order.created`），决定点击后跳到哪。见 {@link resolveNotificationTarget} */
  type: string
  /** 渲染快照的标题 */
  title: string
  /** 渲染快照的正文。模板改动不影响历史消息，这里存的是当时渲染出来的那一段 */
  content: string
  /** 业务 id（工单消息就是工单 id）。为空表示这条消息没有跳转目标 */
  bizId: string | null
  /** 0-未读 1-已读。后端存的就是这两个数字，不在前端翻成布尔 —— 多一层转换只是多一个可能出错的地方 */
  readFlag: number
  readTime: string | null
  createTime: string | null
}

export interface ListNotificationsParams {
  current: number
  size: number
  /** 省略即 ALL */
  readState?: NotificationReadState
}

export function listNotifications(params: ListNotificationsParams): Promise<PageResult<NotificationItem>> {
  return request<PageResult<NotificationItem>>(BASE, {
    query: { current: params.current, size: params.size, readState: params.readState },
  })
}

/**
 * 未读数。铃铛角标用它，也是推送断开期间唯一的兜底（前端 30 秒轮询一次）。
 *
 * 单独一个接口而不是让前端数列表：列表带分页，前端的"看得见的未读数"和
 * "真实的未读数"在翻页后必然对不上。
 */
export function fetchUnreadCount(): Promise<number> {
  return request<number>(`${BASE}/unread-count`)
}

export function markRead(id: string): Promise<void> {
  return request<void>(`${BASE}/${id}/read`, { method: 'POST' })
}

/** 全部已读。返回的是受影响行数，业务上不需要，仅用于日志/排查。 */
export function markAllRead(): Promise<number> {
  return request<number>(`${BASE}/read-all`, { method: 'POST' })
}

export interface SubscribeHandlers {
  /** 流已建立（收到首帧）。用于复位重连退避 —— 没连上时不该退避归位 */
  onOpen?: () => void
  onNotification: (item: NotificationItem) => void
  /** 报文解析失败、网络错误、登录失效都从这里出来 */
  onError?: (error: string) => void
}

/**
 * 订阅消息推送。
 *
 * ## 只是一层薄封装
 * 带 satoken 头、401 走统一登出、解析 SSE 帧这些都在 `openSse` 里，这里只做两件事：
 * 固定路径，以及把 `notification` 那一帧的 JSON 原文解析成 {@link NotificationItem}。
 *
 * ## 这个 Promise 正常结束 ≠ 出错
 * 流是会结束的（服务端重启、网关掐连接、网络切换），而 `openSse` 在流结束时正常返回。
 * 所以**调用方必须自己写重连** —— fetch 版的 SSE 没有 `EventSource` 那种自动重连。
 * 见 `components/MessageBell.tsx` 里的退避循环。
 *
 * ## 解析失败只丢掉这一帧
 * 不往 `onError` 报：推来的这一帧丢了不会让消息消失（轮询会把同一条重新拉到，
 * 而且 store 是按 id 去重的）。为了一个解析错误弹一次红条、或者干脆断掉重连，
 * 是把小概率的噪音放大成用户可见的故障。
 */
export async function subscribeNotifications(
  handlers: SubscribeHandlers,
  signal?: AbortSignal,
): Promise<void> {
  await openSse(`${BASE}/stream`, {
    signal,
    onStart: () => handlers.onOpen?.(),
    onNotification: (raw) => {
      const item = parseNotification(raw)
      if (item) {
        handlers.onNotification(item)
      }
    },
    onError: handlers.onError,
  })
}

/**
 * 消息 → 点击后跳转的前端路径。找不到目标时返回 null（调用方只标记已读，不跳转）。
 *
 * ## 为什么要按 `type` 分派，而不是直接拼 `/ops/work_order/{bizId}`
 * 工单的详情**没有独立路由**：它是在列表页里开一个抽屉（见 `pages/ops/WorkOrder.tsx`）。
 * 所以"跳到某个工单"只能是"跳到列表页 + 带一个 openId 参数过去"。
 *
 * ## 为什么不认识的 type 也照拼一把
 * `bizId` 是个不透明的字符串，工单以外的消息（以后会有）带的东西多半不是工单 id。
 * 猜错的表现是"点进去落在列表页上，什么都没发生"—— 那和"跳转功能坏了"没法区分。
 * 明确返回 null，调用方就能只做标记已读。
 */
export function resolveNotificationTarget(item: NotificationItem): string | null {
  // 建单通知发给超管、派发通知发给受理人，但落点相同：都是那一张工单的详情抽屉
  if ((item.type === 'work_order.created' || item.type === 'work_order.assigned') && item.bizId) {
    return `/ops/work-order?openId=${encodeURIComponent(item.bizId)}`
  }
  return null
}

/** 未读判断。写成一个函数而不是让各处比 `readFlag === 0`，是为了只在一处记住这个约定 */
export function isUnread(item: NotificationItem): boolean {
  return item.readFlag === 0
}

/**
 * 把推送的 JSON 原文解析成一条消息。
 *
 * 只校验"是不是一个带 id 的对象"：这个字段是去重的键，缺了就没法安全地合并进列表。
 * 其余字段一律宽松处理 —— 后端加了字段不用改这里，而少一个 title 也只是显示得空一点，
 * 不值得把它整条丢掉（那才是真的丢消息）。
 */
function parseNotification(raw: string): NotificationItem | null {
  try {
    const parsed = JSON.parse(raw) as unknown
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    const candidate = parsed as Record<string, unknown>
    // id 可能是字符串（后端按雪花 id 的约定出网），也可能是数字（万一哪天漏了转换）。
    // 两种都收下并归一成字符串，否则去重的键会一会儿是 "123" 一会儿是 123。
    if (typeof candidate.id === 'number' && Number.isFinite(candidate.id)) {
      return { ...(candidate as unknown as NotificationItem), id: String(candidate.id) }
    }
    if (typeof candidate.id === 'string' && candidate.id !== '') {
      return candidate as unknown as NotificationItem
    }
    return null
  } catch {
    return null
  }
}
