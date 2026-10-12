import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Badge, Button, Card, Radio, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'

import {
  isUnread,
  listNotifications,
  resolveNotificationTarget,
  type NotificationItem,
  type NotificationReadState,
} from '../../api/message'
import { formatDateTime } from '../../api/workOrder'
import { useNotificationsStore } from '../../store/notifications'

const { Text, Paragraph } = Typography

/**
 * 消息中心（站内信收件箱）。
 *
 * ## 必须分页
 * 消息是只增不减的：每建一张工单就多一条，一年下来是几千行。这和权限、菜单不同，
 * 所以这一页用服务端分页（写法照 `pages/system/PermissionList.tsx`）。
 *
 * ## 未读数不从这里数
 * 标题旁边的未读数取自 `notifications` store —— 和顶栏铃铛是同一个数字。自己数一遍
 * 列表里的未读行会得到一个"当前页的未读数"，翻页就变，而它和角标写的不是一回事。
 *
 * ## 已读操作走 store，而不是直接调接口
 * 界面上的每次已读都会改变角标。直接调 `api/message.ts` 的接口只更新了服务端，
 * 角标要等 30 秒轮询才跟上 —— 表现是"读完了，角标还挂着"。走 store 就是"一份状态、
 * 两处显示"。代价是标记之后要多拉一次列表（多一个请求），这点开销换一次一致，值。
 */
export default function NotificationList() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const unreadCount = useNotificationsStore((state) => state.unreadCount)

  const [records, setRecords] = useState<NotificationItem[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [readState, setReadState] = useState<NotificationReadState>('ALL')
  const [loading, setLoading] = useState(true)
  const [marking, setMarking] = useState(false)

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await listNotifications({ current, size, readState })
        setRecords(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, readState, message],
  )

  useEffect(() => {
    // 挂载时用 silent，避免在 effect 里同步 setState（同其它列表页）
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  const onRead = async (item: NotificationItem) => {
    try {
      await useNotificationsStore.getState().markRead(item.id)
      await load({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '标记已读失败')
    }
  }

  const onMarkAllRead = async () => {
    setMarking(true)
    try {
      await useNotificationsStore.getState().markAllRead()
      message.success('已全部标为已读')
      await load({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败')
    } finally {
      setMarking(false)
    }
  }

  return (
    <Card
      className="page-transition"
      title={
        <Space>
          <span>消息中心</span>
          <Badge count={unreadCount} size="small" />
        </Space>
      }
      extra={
        <Space wrap>
          <Radio.Group
            value={readState}
            onChange={(event) => {
              setReadState(event.target.value as NotificationReadState)
              // 换了筛选要回到第 1 页 —— 否则会停在一个"总数为 0 的第 5 页"上
              setCurrent(1)
            }}
            optionType="button"
          >
            <Radio.Button value="ALL">全部</Radio.Button>
            <Radio.Button value="UNREAD">未读</Radio.Button>
            <Radio.Button value="READ">已读</Radio.Button>
          </Radio.Group>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Button type="primary" disabled={unreadCount === 0} loading={marking} onClick={() => void onMarkAllRead()}>
            全部已读
          </Button>
        </Space>
      }
    >
      <Table<NotificationItem>
        rowKey="id"
        loading={loading}
        dataSource={records}
        scroll={{ x: 'max-content' }}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, pageSize) => {
            setCurrent(page)
            setSize(pageSize)
          },
        }}
        columns={[
          {
            title: '状态',
            width: 80,
            render: (_, record) =>
              isUnread(record) ? <Tag color="blue">未读</Tag> : <Tag>已读</Tag>,
          },
          {
            title: '消息',
            // 不设 width：这一列吃掉剩余宽度，标题与正文才有地方折行
            render: (_, record) => (
              <div>
                <Text strong={isUnread(record)}>{record.title}</Text>
                <Paragraph
                  type="secondary"
                  // 正文可能很长（模板正文没有长度上限），列表里只给两行 ——
                  // 完整的正文本来就在这条业务本身里，消息只是"提示你去看看"
                  ellipsis={{ rows: 2 }}
                  style={{ margin: '4px 0 0', fontSize: 12 }}
                >
                  {record.content}
                </Paragraph>
              </div>
            ),
          },
          {
            title: '时间',
            width: 170,
            render: (_, record) => <Text type="secondary">{formatDateTime(record.createTime)}</Text>,
          },
          {
            title: '操作',
            width: 160,
            render: (_, record) => {
              const target = resolveNotificationTarget(record)
              return (
                <Space>
                  {target ? (
                    <Button
                      type="link"
                      size="small"
                      onClick={() => {
                        // 点"查看"也顺带标记已读：用户已经看到这条消息了，
                        // 再要求他点两次"标记已读"没有任何道理。
                        if (isUnread(record)) {
                          void onRead(record)
                        }
                        navigate(target)
                      }}
                    >
                      查看
                    </Button>
                  ) : (
                    // 没有跳转目标时如实说明，而不是给一个点了没反应的按钮
                    <Tooltip title="这条消息没有关联的业务页面">
                      <span style={{ color: 'rgba(0,0,0,0.25)' }}>无关联</span>
                    </Tooltip>
                  )}
                  <Button type="link" size="small" disabled={!isUnread(record)} onClick={() => void onRead(record)}>
                    标为已读
                  </Button>
                </Space>
              )
            },
          },
        ]}
      />
    </Card>
  )
}
