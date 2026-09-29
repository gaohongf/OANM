import { useState } from 'react'
import { App as AntdApp, Button, Card, Descriptions, InputNumber, Space, Tag, Typography } from 'antd'

import { request } from '../../api/http'

const { Text } = Typography

/** 工单（对应后端 WorkOrderRes） */
interface WorkOrder {
  id: string
  name: string
  /**
   * 这两个字段在后端是 `@User Long`，序列化时会被替换成用户对象。
   * 注意它是**运行时才变形**的：解析成功是 `{id, username, ...}`，
   * 解析失败（用户服务不可用）会降级成 `{id}` —— 所以这里的类型要允许字段缺失。
   */
  createBy: ResolvedUser | null
  updateBy: ResolvedUser | null
}

interface ResolvedUser {
  id: number
  username?: string
  nickname?: string
}

/**
 * 工单页。用来演示后端 `@User` 的字段解析效果。
 *
 * 注意这里展示的 `createBy` 后端只存了一个 id，是**序列化阶段**才被换成用户对象的 ——
 * 所以这一页同时也能反映用户服务是否可用：不可用时字段会降级成只剩 id（而不是报错）。
 */
export default function WorkOrderPage() {
  const { message } = AntdApp.useApp()
  // 后端这个演示端点的路径变量是工单号（固定 1），userId 是"把创建人/更新人解析成哪个用户"
  const [userId, setUserId] = useState<number>(1)
  const [order, setOrder] = useState<WorkOrder | null>(null)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const data = await request<WorkOrder>('/api/ops/work_order/1', {
        query: { userId },
      })
      setOrder(data)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card
      title="工单详情"
      extra={
        <Space>
          <Text type="secondary">解析成用户</Text>
          <InputNumber
            min={1}
            value={userId}
            onChange={(value) => setUserId(value ?? 1)}
            style={{ width: 110 }}
            addonBefore="id"
          />
          <Button type="primary" loading={loading} onClick={load}>
            加载
          </Button>
        </Space>
      }
    >
      {!order ? (
        <Text type="secondary">点「加载」请求一条工单。</Text>
      ) : (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="名称">{order.name}</Descriptions.Item>
          <Descriptions.Item label="创建人">
            <UserCell user={order.createBy} />
          </Descriptions.Item>
          <Descriptions.Item label="更新人">
            <UserCell user={order.updateBy} />
          </Descriptions.Item>
        </Descriptions>
      )}

      <Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
        创建人/更新人在库里只存了 id，是后端在序列化时换成用户对象的。
        如果把 service-auth 停掉再加载，这里会降级成只显示 id —— 而不是报错。
      </Text>
    </Card>
  )
}

/** 展示一个被后端解析过的用户字段。缺字段说明发生了降级。 */
function UserCell({ user }: { user: ResolvedUser | null }) {
  if (!user) {
    return <Text type="secondary">无</Text>
  }
  if (!user.username) {
    return (
      <Space>
        <Tag color="warning">已降级</Tag>
        <Text>id={user.id}</Text>
      </Space>
    )
  }
  return (
    <Space>
      <Text strong>{user.nickname || user.username}</Text>
      <Text type="secondary">
        {user.username} (id={user.id})
      </Text>
    </Space>
  )
}
