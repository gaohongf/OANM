import { Space, Tag, Typography } from 'antd'

import { findOption, PRIORITY_OPTIONS, STATUS_OPTIONS, TYPE_OPTIONS } from '../../../api/workOrder'
import type { WorkOrderPriority, WorkOrderStatus, WorkOrderType } from '../../../api/workOrder'
import type { ResolvedUser } from '../../../types/api'

const { Text } = Typography

/**
 * 工单表格里几个带枚举的单元格。
 *
 * 都做"取不到元数据就把原始值显出来"的处理：如果后端哪天多了一个枚举值，
 * 界面应该如实显示出那个值（一眼能看出是"没见过的值"），而不是显示空白或者
 * 伪装成最像的那个 —— 后者会让数据问题看起来像界面问题。
 */

export function TypeTag({ type }: { type: WorkOrderType }) {
  const option = findOption(TYPE_OPTIONS, type)
  return <Tag color={option?.color}>{option?.label ?? type}</Tag>
}

export function PriorityTag({ priority }: { priority: WorkOrderPriority }) {
  const option = findOption(PRIORITY_OPTIONS, priority)
  return <Tag color={option?.color}>{option?.label ?? priority}</Tag>
}

export function StatusTag({ status }: { status: WorkOrderStatus }) {
  const option = findOption(STATUS_OPTIONS, status)
  return <Tag color={option?.color}>{option?.label ?? status}</Tag>
}

/**
 * 被后端 `@User` 解析过的用户字段。
 *
 * 库里只存 id，是序列化阶段才换成用户对象的，所以这里同时反映着用户服务是否可用：
 * 解析成功显示昵称/用户名，降级（比如 service-auth 停了）则只剩 id —— 而不是报错。
 * 降级时特意把这件事标出来，否则"只有一串数字"看起来像是数据脏了。
 */
export function UserCell({ user }: { user: ResolvedUser | null }) {
  if (!user) {
    return <Text type="secondary">-</Text>
  }
  if (!user.username) {
    return (
      <Space size={4}>
        <Tag color="warning">已降级</Tag>
        <Text type="secondary">id={user.id}</Text>
      </Space>
    )
  }
  return <Text>{user.nickname || user.username}</Text>
}
