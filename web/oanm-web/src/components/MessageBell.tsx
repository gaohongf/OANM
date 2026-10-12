import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { App as AntdApp, Badge, Button, Empty, Popover, Typography, theme } from 'antd'
import { BellOutlined } from '@ant-design/icons'

import {
  isUnread,
  resolveNotificationTarget,
  subscribeNotifications,
  type NotificationItem,
} from '../api/message'
import { formatDateTime } from '../api/workOrder'
import { useNotificationsStore } from '../store/notifications'

const { Text, Paragraph } = Typography

/**
 * 顶栏的消息铃铛。
 *
 * ## 一条消息从服务端到这里的完整路径
 * 1. **轮询**（30 秒）：`store.startPolling()` 拉"最近 20 条 + 未读数"。这是权威路径 ——
 *    只要页面开着，消息迟早会出现在角标上，与推送是否可用无关。
 * 2. **推送**（SSE）：连上时几乎立刻到，走 `applyIncoming` 合并进同一个 store（按 id 去重）。
 *    两条路径写的是同一份状态，所以不会重复计数。
 *
 * ## 为什么要自己写重连
 * `openSse` 是 fetch 实现 —— 浏览器不会替我们重连（`EventSource` 会，但它带不上 satoken 头）。
 * 而这条流注定会断：网关重启、网络切换、笔记本合盖。断了就是"铃铛从此不响"，
 * 所以这里有一个 5 秒起步、最多 60 秒的退避重连循环。
 *
 * ## 退避为什么要看"连了多久"
 * 服务端如果"接受连接然后立刻断"（例如网关健康检查通过但后端刚启动完），
 * 一个无条件归位的退避会变成每 5 秒一次的热循环。所以只有连接稳定存活过
 * {@link STABLE_MS} 才把退避归位，秒断秒连则继续按指数退避。
 *
 * ## 断线不提示用户
 * 因为它不是故障：轮询还在跑，消息照收，只是慢一点（最多 30 秒）。
 * 为一件用户感知不到的事弹提示，只会让人来问"是不是坏了"。
 */

/** 重连退避的起点 */
const RECONNECT_BASE_MS = 5_000

/** 重连退避的上限。再长下去，一次短暂断线的恢复会慢到用户以为功能挂了 */
const RECONNECT_MAX_MS = 60_000

/** 连接稳定存活超过这个时长，才认为"上一次断开是偶发"，把退避归位 */
const STABLE_MS = 10_000

export function MessageBell() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { token } = theme.useToken()

  const items = useNotificationsStore((state) => state.items)
  const unreadCount = useNotificationsStore((state) => state.unreadCount)
  const loaded = useNotificationsStore((state) => state.loaded)

  const [open, setOpen] = useState(false)

  useEffect(() => {
    // store 的这几个动作引用是稳定的（zustand 的 set/get 闭包），所以这个 effect
    // 只在挂载/卸载时跑一次 —— 依赖写全只是为了说明"它确实不依赖任何渲染期变量"。
    const store = useNotificationsStore.getState()
    store.startPolling()

    const controller = new AbortController()
    let delay = RECONNECT_BASE_MS
    let connectedAt = 0

    const loop = async () => {
      while (!controller.signal.aborted) {
        await subscribeNotifications(
          {
            onOpen: () => {
              connectedAt = Date.now()
            },
            onNotification: (item) => useNotificationsStore.getState().applyIncoming(item),
            // 流内错误（报文异常、服务端异常）不提示：除了"断线"它们都没有用户能做
            // 的动作，而断线由下面的重连兜底。
            onError: () => undefined,
          },
          controller.signal,
        )

        if (controller.signal.aborted) {
          return
        }
        if (connectedAt !== 0 && Date.now() - connectedAt >= STABLE_MS) {
          delay = RECONNECT_BASE_MS
        }
        await sleep(delay, controller.signal)
        delay = Math.min(delay * 2, RECONNECT_MAX_MS)
      }
    }

    void loop()

    return () => {
      controller.abort()
      store.stopPolling()
    }
  }, [])

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      // 展开时立刻拉一次：用户点开就是想看"现在有什么"，等下一轮轮询（最多 30 秒）
      // 会让这个动作看起来没有响应。
      void useNotificationsStore.getState().refresh()
    }
  }

  const onSelect = async (item: NotificationItem) => {
    setOpen(false)

    if (isUnread(item)) {
      try {
        await useNotificationsStore.getState().markRead(item.id)
      } catch (error) {
        // 标记失败不拦住跳转 —— 用户点这条消息的目的是"去看它"。
        // 提一句是为了解释"角标怎么没动"，否则会以为点空了。
        message.warning(error instanceof Error ? error.message : '标记已读失败')
      }
    }

    const target = resolveNotificationTarget(item)
    if (target) {
      navigate(target)
    }
  }

  const onMarkAllRead = async () => {
    try {
      await useNotificationsStore.getState().markAllRead()
      message.success('已全部标为已读')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败')
    }
  }

  const content = (
    <div style={{ width: 340 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingBottom: 8,
          borderBottom: `1px solid ${token.colorSplit}`,
        }}
      >
        <Text strong>消息</Text>
        <Button type="link" size="small" disabled={unreadCount === 0} onClick={() => void onMarkAllRead()}>
          全部已读
        </Button>
      </div>

      <div style={{ maxHeight: 320, overflowY: 'auto' }}>
        {items.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={loaded ? '暂无消息' : '加载中'}
            style={{ padding: '16px 0' }}
          />
        ) : (
          items.map((item) => (
            <div
              key={item.id}
              role="button"
              tabIndex={0}
              onClick={() => void onSelect(item)}
              onKeyDown={(event) => {
                // 键盘可达：这一行是 div 而不是 Button（Button 在 Popover 里会把
                // 文字样式全部接管），所以回车/空格要自己接上。
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  void onSelect(item)
                }
              }}
              style={{ padding: '10px 4px', borderBottom: `1px solid ${token.colorSplit}`, cursor: 'pointer' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                {/* 未读圆点。用一个点而不是把整行加粗 —— 圆点不改变文字排版，
                    已读/未读切换时行高不会跳 */}
                <span
                  style={{
                    width: 6,
                    height: 6,
                    flex: '0 0 auto',
                    borderRadius: '50%',
                    background: isUnread(item) ? token.colorPrimary : 'transparent',
                  }}
                />
                <Text strong={isUnread(item)} ellipsis style={{ flex: 1 }}>
                  {item.title}
                </Text>
                <Text type="secondary" style={{ fontSize: 12, flex: '0 0 auto' }}>
                  {formatDateTime(item.createTime)}
                </Text>
              </div>
              <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ margin: '4px 0 0 12px', fontSize: 12 }}>
                {item.content}
              </Paragraph>
            </div>
          ))
        )}
      </div>

      <div style={{ paddingTop: 8, textAlign: 'center' }}>
        <Button
          type="link"
          size="small"
          onClick={() => {
            setOpen(false)
            navigate('/message/list')
          }}
        >
          查看全部
        </Button>
      </div>
    </div>
  )

  return (
    <Popover
      open={open}
      onOpenChange={onOpenChange}
      trigger="click"
      placement="bottomRight"
      content={content}
      arrow={false}
    >
      <Badge count={loaded ? unreadCount : 0} size="small" offset={[-2, 4]}>
        <Button type="text" aria-label="消息" icon={<BellOutlined />} />
      </Badge>
    </Popover>
  )
}

/**
 * 可被打断的等待。
 *
 * 卸载时 `controller.abort()` 必须能把"正在退避的那几秒"也叫醒 —— 否则用户退出登录后，
 * 那个定时器还会在后台活着，并在到期时对已经失效的 token 再发一次请求。
 */
function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const onAbort = () => {
      clearTimeout(timer)
      resolve()
    }
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    signal.addEventListener('abort', onAbort, { once: true })
  })
}
