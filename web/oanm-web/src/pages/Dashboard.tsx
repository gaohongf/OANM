import { Card, Descriptions, Tag, Typography } from 'antd'

import { useAuthStore } from '../store/auth'

const { Text } = Typography

/**
 * 首页 / 概览。
 *
 * 这一页刻意不请求任何后端接口 —— 它必须对**任何已登录用户**可用，
 * 包括一个角色都没有的新用户。如果首页依赖某个权限才能加载，新用户登录后
 * 就会看到一个报错的首页，而真正的原因只是他还没被授权。
 */
export default function Dashboard() {
  const user = useAuthStore((state) => state.user)
  const roles = useAuthStore((state) => state.roles)
  const permissions = useAuthStore((state) => state.permissions)

  return (
    <Card title="概览">
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label="当前用户">
          {user?.nickname || user?.username} (id={user?.id})
        </Descriptions.Item>
        <Descriptions.Item label="角色">
          {roles.length > 0 ? roles.map((role) => <Tag key={role}>{role}</Tag>) : <Text type="secondary">无</Text>}
        </Descriptions.Item>
        <Descriptions.Item label="权限数">
          {permissions.length}
          {permissions.includes('*') && <Tag color="gold" style={{ marginInlineStart: 8 }}>超级管理员</Tag>}
        </Descriptions.Item>
      </Descriptions>

    </Card>
  )
}
