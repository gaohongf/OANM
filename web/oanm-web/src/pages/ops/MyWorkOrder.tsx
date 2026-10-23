import { WorkOrderList } from './components/WorkOrderList'

/**
 * 我的工单：在「我提交的」和「我受理的」之间切换。
 *
 * 两个范围都由后端按当前登录人过滤（create_by / assignee_id），
 * 前端只传范围，不传用户 id。
 */
export default function MyWorkOrderPage() {
  return <WorkOrderList mode="mine" />
}
