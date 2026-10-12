import { create } from 'zustand'

import * as messageApi from '../api/message'
import type { NotificationItem } from '../api/message'

/**
 * 站内信状态（铃铛角标 + 最近若干条）。
 *
 * ## 为什么要有这一层，而不是让铃铛和消息页各请求各的
 * 未读数被两个地方同时改：SSE 推来一条（+1）、用户在列表页点了一条（-1）。
 * 各存各的状态时，这个数字必然会在某一次操作后开始漂移 —— 表现是"列表里全读完了，
 * 角标还挂着 3"。所以两份 UI 读写的是同一份状态。
 *
 * ## 轮询是权威路径，推送只是"更快"
 * 30 秒拉一次服务端：这个数字以服务端的计数为准，前端的加减只是在等待期间让界面
 * 立刻有反应。任何一次漂移都会在下一轮被拉平 —— 这也是为什么推送断线不需要告诉用户。
 *
 * ## 为什么不做成"消息列表的唯一真相"
 * 消息中心页面是服务端分页的（带未读/已读筛选），它的数据量由页码决定，不适合塞进
 * store。这里只保留最近 {@link RECENT_LIMIT} 条给铃铛下拉用 —— 那是个"瞥一眼"的入口，
 * 真正要翻消息时去消息中心页。
 */

/** 铃铛下拉里保留的条数 */
const RECENT_LIMIT = 20

/**
 * 轮询间隔。
 *
 * 取 30 秒是因为它要在"推送没连上"时当唯一的兜底：慢了会让用户以为通知坏了，
 * 快了就是一个纯粹的定时压力（每个在线用户每 30 秒 2 个请求，服务端完全不痛）。
 */
const POLL_INTERVAL_MS = 30_000

/**
 * 定时器放在模块级而不是 state 里。
 *
 * **不是为了省一次渲染**：放进 state 的话，`startPolling` 会因为它自身的改变而重新
 * 创建，`useEffect` 的依赖随之变化 → 清理 → 重启定时器，于是"永远也等不到第一次 30 秒"。
 * 这类自指的定时器只该存在于 React 与 zustand 的生命周期之外。
 */
let pollTimer: ReturnType<typeof setInterval> | null = null

interface NotificationsState {
  /** 最近的消息，按 id 倒序（后端就是倒序给的） */
  items: NotificationItem[]
  unreadCount: number
  /** 是否已经成功拉过一次。为 false 时角标不显示数字，避免先闪一个 0 再跳到真实值 */
  loaded: boolean

  /** 拉一次"最近列表 + 未读数"。推送断线期间由轮询反复调用 */
  refresh: () => Promise<void>
  /** 推来一条。**按 id 去重** —— 同一条消息可能既被推来、又被下一轮轮询拉到 */
  applyIncoming: (item: NotificationItem) => void
  /** 单条已读。会同步更新未读数（调用方负责展示失败提示） */
  markRead: (id: string) => Promise<void>
  /** 全部已读 */
  markAllRead: () => Promise<void>
  /** 开始/停止 30 秒轮询。两者都是幂等的，可以放心地被多个组件调用 */
  startPolling: () => void
  stopPolling: () => void
}

export const useNotificationsStore = create<NotificationsState>((set, get) => ({
  items: [],
  unreadCount: 0,
  loaded: false,

  async refresh() {
    try {
      // 两个请求并发：它们之间没有依赖，串行只会让"点了刷新之后要等两轮网络"。
      // 不一致的窗口（先拿到列表、后拿到计数）无所谓 —— 下一轮 30 秒就拉平了。
      const [page, unread] = await Promise.all([
        messageApi.listNotifications({ current: 1, size: RECENT_LIMIT }),
        messageApi.fetchUnreadCount(),
      ])
      set({ items: page.records, unreadCount: unread, loaded: true })
    } catch {
      // 刻意静默：轮询失败不该弹提示（网络抖一下弹一个红条，而下一轮就好了），
      // 也不该把界面清空成 0 —— 保留上一次的数据比"假装没有消息"更接近真相。
      // 登录失效不归这里管：http 层遇到 401 已经统一走登出了。
    }
  },

  applyIncoming(item) {
    const { items, unreadCount } = get()
    // 已经在列表里就什么都不做。重复到达是<b>正常</b>的：Kafka 至少一次投递 + 30 秒轮询
    // 会把同一条再拉一次。不去重的话未读数会随着"推一次 + 拉一次"越涨越多。
    if (items.some((existing) => existing.id === item.id)) {
      return
    }
    set({
      items: [item, ...items].slice(0, RECENT_LIMIT),
      unreadCount: item.readFlag === 0 ? unreadCount + 1 : unreadCount,
      loaded: true,
    })
  },

  async markRead(id) {
    const { items, unreadCount } = get()
    const target = items.find((item) => item.id === id)
    // 已经读过就直接返回：接口是幂等的，但白跑一趟没有任何收益。
    // 注意只有"确实在本地看到它是已读"才跳过 —— 找不到（消息在更早的分页里）时照发，
    // 那时它很可能就是未读的。
    if (target && target.readFlag !== 0) {
      return
    }

    await messageApi.markRead(id)

    // 本地先减：等 30 秒轮询才更新的话，用户点完角标纹丝不动，会以为没点上。
    // 减到 0 就停住（`Math.max`）—— 本地计数终究是个估计值，负数看起来像 bug。
    // readTime 用本地时间填只是为了让"已读时间"这一格立刻有内容，下一轮轮询会用
    // 服务端的值覆盖掉它。
    set({
      items: items.map((item) =>
        item.id === id ? { ...item, readFlag: 1, readTime: item.readTime ?? new Date().toISOString() } : item,
      ),
      unreadCount: Math.max(0, unreadCount - 1),
    })
  },

  async markAllRead() {
    await messageApi.markAllRead()
    // 未读数直接归 0，不按条数减：服务端刚把<b>全部</b>标成已读，这里再算一遍只会算漏
    // （列表里只有最近 20 条，第 21 条以后的未读根本不在本地）。
    set({
      items: get().items.map((item) =>
        item.readFlag === 0
          ? { ...item, readFlag: 1, readTime: item.readTime ?? new Date().toISOString() }
          : item,
      ),
      unreadCount: 0,
    })
  },

  startPolling() {
    if (pollTimer !== null) {
      return
    }
    // 先立刻拉一次再挂定时器：等 30 秒才出现角标，用户会以为通知功能没接上。
    void get().refresh()
    pollTimer = setInterval(() => {
      void get().refresh()
    }, POLL_INTERVAL_MS)
  },

  stopPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  },
}))
