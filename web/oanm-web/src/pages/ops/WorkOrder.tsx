import { WorkOrderList } from './components/WorkOrderList'

/**
 * 工单管理：看全部工单。
 *
 * 列表、详情抽屉、接取/派发都与「我的工单」共用 `WorkOrderList`，
 * 只是这里不按提交人/受理人过滤。
 */
export default function WorkOrderPage() {
  return <WorkOrderList mode="manage" />
}
