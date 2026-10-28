import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { MenuProps } from 'antd'
import {
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router'

import { AI_PERMISSIONS } from '../../../api/ai'
import { listUsersByPermission } from '../../../api/user'
import type { AdminUserItem } from '../../../api/user'
import {
  assignWorkOrder,
  cancelWorkOrder,
  claimWorkOrder,
  decideCancelWorkOrder,
  formatDateTime,
  getWorkOrder,
  listWorkOrders,
  resolveWorkOrder,
  splitRounds,
  STATUS_OPTIONS,
  WORK_ORDER_PERMISSIONS,
} from '../../../api/workOrder'
import type {
  WorkOrderDetail,
  WorkOrderRow,
  WorkOrderScope,
  WorkOrderStatus,
} from '../../../api/workOrder'
import { Auth } from '../../../permission/Auth'
import { useAuthStore } from '../../../store/auth'
import { ManualWorkOrderDrawer } from './ManualWorkOrderDrawer'
import { PriorityTag, StatusTag, TypeTag, UserCell } from './WorkOrderCells'

const { Text, Paragraph } = Typography

// Select 只认 {value,label}，元数据里还带着颜色，这里摘一下
const STATUS_SELECT_OPTIONS = STATUS_OPTIONS.map((item) => ({ value: item.value, label: item.label }))

const MINE_SCOPE_OPTIONS: { label: string; value: WorkOrderScope }[] = [
  { label: '我提交的', value: 'created' },
  { label: '我受理的', value: 'assigned' },
]

export type WorkOrderListMode = 'manage' | 'mine'

interface WorkOrderListProps {
  /**
   * `manage`：全部工单；`mine`：只剩「我提交的 / 我受理的」两个范围。
   * 两个页面共用同一套表格、详情抽屉、接取/派发逻辑，避免复制一份后慢慢漂移。
   */
  mode: WorkOrderListMode
}

/**
 * 工单列表（工单管理 / 我的工单共用）。
 *
 * ## 列表与详情是两个接口
 * 表格只取摘要（标题/类型/优先级/状态/创建人/时间），正文（描述、原始描述）由抽屉
 * 按 id 单独拉。原因是描述和用户原话可能是长文，一页 10 行可能就有十几 KB，
 * 而它们要打开抽屉才看得到。
 *
 * ## 创建人这一列在演示什么
 * 库里只存了 `create_by` 的 id，是后端**序列化时**换成用户对象的。所以这一列同时也是
 * 一块探针：service-auth 不可用时它会降级成「已降级 + id」，而不是整页报错。
 *
 * ## 分页与筛选
 * 照仓库里其它列表页的写法：`onChange` 只改 state，重新拉取交给 `useEffect`；
 * 换搜索词/换状态/换范围都要回到第 1 页，否则会停在一个"总数为 0 的第 5 页"上。
 */
export function WorkOrderList({ mode }: WorkOrderListProps) {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  // 后端把 @User 字段解析成字符串 id；按钮的显隐只认解析成功的那一种形态。
  // 降级成裸数字时判断不可靠（雪花 id 可能已经丢精度），宁可不显示，
  // 也不要把“结单”显示给一个其实不是受理人的人 —— 后端仍会兜底拒绝。
  const currentUserId = useAuthStore((state) => state.user?.id)
  // 建单的两条路各有自己的权限。下拉项是数据，没法用 <Auth> 包，所以直接取布尔值。
  const canUseAi = useAuthStore((state) => state.can(AI_PERMISSIONS.intentInference))
  const canCreate = useAuthStore((state) => state.can(WORK_ORDER_PERMISSIONS.create))

  const [rows, setRows] = useState<WorkOrderRow[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<WorkOrderStatus | undefined>(undefined)
  const [mineScope, setMineScope] = useState<WorkOrderScope>('created')
  const [loading, setLoading] = useState(true)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [detail, setDetail] = useState<WorkOrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()

  const [dispatchTarget, setDispatchTarget] = useState<WorkOrderRow | null>(null)
  const [candidates, setCandidates] = useState<AdminUserItem[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [candidateError, setCandidateError] = useState<string | null>(null)
  const [selectedAssignee, setSelectedAssignee] = useState<string | undefined>(undefined)
  const [dispatching, setDispatching] = useState(false)

  const [resolveTarget, setResolveTarget] = useState<WorkOrderRow | null>(null)
  const [resolveNote, setResolveNote] = useState('')
  const [resolving, setResolving] = useState(false)

  // 管理页不按人过滤；我的工单页由 Segmented 决定看提交的还是受理的。
  const scope = mode === 'mine' ? mineScope : undefined

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await listWorkOrders({
          current,
          size,
          // 空串不要发过去 —— 后端会把它当成一个真的搜索词
          keyword: keyword.trim() || undefined,
          status,
          scope,
        })
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, status, scope, message],
  )

  useEffect(() => {
    // 挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  /**
   * 手动建单成功后让新单出现在列表里。
   *
   * 新单按创建时间倒序，一定在第 1 页；「我的工单」如果正停在「我受理的」，
   * 刚提交的单也不在那个范围里，所以切回「我提交的」。这两处 state 变化会让
   * 上面的 effect 重新拉一次；两者都没变时（本来就在第 1 页、本来就在看我提交的），
   * state 不会变，effect 不会触发，需要手动拉一次。
   */
  const onManualCreated = () => {
    const switchingScope = mode === 'mine' && mineScope !== 'created'
    const leavingFirstPage = current !== 1
    if (switchingScope) {
      setMineScope('created')
    }
    if (leavingFirstPage) {
      setCurrent(1)
    }
    if (!switchingScope && !leavingFirstPage) {
      void load()
    }
  }

  // 两个入口按权限各自显示：有智能提单权限才出现 AI 路，有建单权限才出现手动路。
  const createMenuItems: MenuProps['items'] = []
  if (canUseAi) {
    createMenuItems.push({ key: 'ai', icon: <ThunderboltOutlined />, label: '智能提单' })
  }
  if (canCreate) {
    createMenuItems.push({ key: 'manual', icon: <EditOutlined />, label: '手动填写' })
  }

  const openDetailById = useCallback(
    async (id: string) => {
      setDrawerOpen(true)
      setDetail(null)
      setDetailLoading(true)
      try {
        setDetail(await getWorkOrder(id))
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载详情失败')
        // 拉不到就关掉抽屉，而不是留一个空的骨架在转
        setDrawerOpen(false)
      } finally {
        setDetailLoading(false)
      }
    },
    [message],
  )

  /** 如果这张单的详情抽屉正开着，重新取一次，让抽屉里的状态/受理人跟着列表一起变 */
  const refreshOpenDetail = useCallback(
    async (id: string) => {
      if (drawerOpen && detail?.id === id) {
        await openDetailById(id)
      }
    },
    [drawerOpen, detail, openDetailById],
  )

  const onClaim = async (record: WorkOrderRow) => {
    try {
      await claimWorkOrder(record.id)
      message.success('已接取')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接取失败')
    }
    // 失败最常见的原因是"刚被别人接走"，所以无论成败都刷新一次，让列表回到真实状态。
    await load()
    await refreshOpenDetail(record.id)
  }

  const openDispatch = async (record: WorkOrderRow) => {
    setDispatchTarget(record)
    // 改派时预选当前受理人；降级成裸数字时拿不到字符串 id，就不预选
    setSelectedAssignee(
      record.assigneeId !== null && typeof record.assigneeId === 'object'
        ? record.assigneeId.id
        : undefined,
    )
    setCandidates([])
    setCandidateError(null)
    setCandidateLoading(true)
    try {
      const page = await listUsersByPermission(WORK_ORDER_PERMISSIONS.claim)
      setCandidates(page.records)
    } catch (error) {
      // 包括 403（没有用户列表权限）：把后端的提示露出来，不要留一个空下拉让人猜
      setCandidateError(error instanceof Error ? error.message : '加载候选人失败')
    } finally {
      setCandidateLoading(false)
    }
  }

  const onDispatch = async () => {
    if (!dispatchTarget || !selectedAssignee) {
      message.warning('请选择受理人')
      return
    }
    const workOrderId = dispatchTarget.id
    setDispatching(true)
    try {
      await assignWorkOrder(workOrderId, selectedAssignee)
      message.success('已派发')
      setDispatchTarget(null)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '派发失败')
    } finally {
      setDispatching(false)
    }
    await load()
    await refreshOpenDetail(workOrderId)
  }

  /** 只认解析成功的用户对象；降级成裸数字时无法可靠判断“是不是我”。 */
  const isAssigneeOf = (record: WorkOrderRow) => {
    const id = resolvedUserId(record.assigneeId)
    return id !== null && id === currentUserId
  }
  const isCreatorOf = (record: WorkOrderRow) => {
    const id = resolvedUserId(record.createBy)
    return id !== null && id === currentUserId
  }
  const hasCancelRequest = (record: WorkOrderRow) => Boolean(record.cancelRequestedAt)
  const canResolve = (record: WorkOrderRow) =>
    record.status === 'ACCEPTED' && isAssigneeOf(record) && !hasCancelRequest(record)

  const openResolve = (record: WorkOrderRow) => {
    setResolveTarget(record)
    setResolveNote('')
  }

  const onResolve = async () => {
    if (!resolveTarget) {
      return
    }
    const workOrderId = resolveTarget.id
    setResolving(true)
    try {
      await resolveWorkOrder(workOrderId, resolveNote)
      message.success('已结单')
      setResolveTarget(null)
      setResolveNote('')
      await load()
      await refreshOpenDetail(workOrderId)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '结单失败')
    } finally {
      setResolving(false)
    }
  }

  const canCancel = (record: WorkOrderRow) =>
    isCreatorOf(record) &&
    (record.status === 'NEW' || record.status === 'ACCEPTED') &&
    !hasCancelRequest(record)
  const canDecideCancel = (record: WorkOrderRow) =>
    hasCancelRequest(record) && isAssigneeOf(record)

  /**
   * 取消会走哪条路。与后端规则一致：没人受理、或提单人自己就是受理人时直接关闭；
   * 其余已受理的情况才发申请。只用来决定提示文案。
   */
  const cancelsDirectly = (record: WorkOrderRow) =>
    record.status === 'NEW' || isAssigneeOf(record)

  const cancelDescription = (record: WorkOrderRow) => {
    if (record.status === 'NEW') {
      return '取消后工单状态将变为「已关闭」。'
    }
    if (isAssigneeOf(record)) {
      return '你是该工单的受理人，确认后直接关闭。'
    }
    return '这张工单已被受理，将向受理人发送取消申请，受理人同意后工单才会关闭。'
  }

  const onCancel = async (record: WorkOrderRow) => {
    const workOrderId = record.id
    const direct = cancelsDirectly(record)
    try {
      await cancelWorkOrder(workOrderId)
      message.success(direct ? '工单已关闭' : '已提交取消申请，等待受理人确认')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消失败')
    }
    // 无论成败都刷新：工单仍然存在，直接关闭时抽屉里的状态也要跟着变。
    await load()
    await refreshOpenDetail(workOrderId)
  }

  const onDecideCancel = async (record: WorkOrderRow, approved: boolean) => {
    const workOrderId = record.id
    try {
      await decideCancelWorkOrder(workOrderId, approved)
      message.success(approved ? '工单已关闭' : '已拒绝取消申请')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '处理取消申请失败')
    }
    await load()
    await refreshOpenDetail(workOrderId)
  }

  /**
   * 支持从消息中心直接落到某一条工单：`?openId=<工单id>`。
   *
   * 「我的工单」还会带 `?scope=created|assigned`：取消申请的通知要落在「我受理的」，
   * 取消结果的通知要落在「我提交的」。两个参数必须<b>在同一个 effect 里消费</b>：
   * 分成两个 effect 各自 `setSearchParams` 时，它们都基于同一份旧参数构造新地址，
   * 后执行的那个会把另一个刚删掉的键写回来 —— 表现是地址栏来回跳，甚至抽屉反复弹出。
   *
   * ## 为什么是查询参数而不是 `/ops/work-order/:id` 这样的详情路由
   * 详情没有独立路由 —— 它是这一页里的一个抽屉（列表与详情是两个接口，见类注释）。
   * 为了一条通知就去新增一条路由 + 一个新页面，等于把"打开抽屉"这件事做两遍。
   *
   * ## 参数用过就摘掉
   * 留着它的话，用户关掉抽屉、再刷新页面，抽屉又会自己弹出来 —— 而"关掉"是有意的。
   * 摘掉之后地址栏也随之干净，可以放心把链接发给别人。
   */
  useEffect(() => {
    const openId = searchParams.get('openId')
    const scopeParam = searchParams.get('scope')
    const next = new URLSearchParams(searchParams)
    let changed = false

    if (mode === 'mine' && (scopeParam === 'created' || scopeParam === 'assigned')) {
      // eslint-disable-next-line react/set-state-in-effect
      setMineScope(scopeParam)
      next.delete('scope')
      changed = true
    }
    if (openId) {
      next.delete('openId')
      changed = true
    }
    if (changed) {
      setSearchParams(next, { replace: true })
    }
    if (openId) {
      // 这里的 setState 正是"用 effect 同步外部系统"那条规则允许的场景：抽屉该不该开
      // 由地址栏决定，而不是由某次点击决定。
      // eslint-disable-next-line react/set-state-in-effect
      void openDetailById(openId)
    }
  }, [mode, searchParams, setSearchParams, openDetailById])

  return (
    <Card
      className="page-transition"
      title={mode === 'mine' ? '我的工单' : '工单管理'}
      extra={
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="按标题或描述搜索"
            enterButton={<SearchOutlined />}
            onSearch={(value) => {
              setKeyword(value)
              setCurrent(1)
            }}
            style={{ width: 240 }}
          />
          <Select<WorkOrderStatus>
            allowClear
            placeholder="全部状态"
            options={STATUS_SELECT_OPTIONS}
            value={status}
            onChange={(value) => {
              setStatus(value)
              setCurrent(1)
            }}
            style={{ width: 140 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          {/* 建单有两条路：AI 先理解问题再填，或自己逐项填写。
              没有配置 AI key 时手动入口仍可正常提交，两个入口按各自权限显示 */}
          {createMenuItems.length > 0 && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: createMenuItems,
                onClick: ({ key }) => {
                  if (key === 'ai') {
                    navigate('/ops/ai/intent')
                  } else if (key === 'manual') {
                    setManualOpen(true)
                  }
                },
              }}
            >
              <Button type="primary" icon={<PlusOutlined />}>
                新建工单
                <DownOutlined style={{ marginInlineStart: 4, fontSize: 10 }} />
              </Button>
            </Dropdown>
          )}
        </Space>
      }
    >
      {mode === 'mine' && (
        <Segmented<WorkOrderScope>
          value={mineScope}
          options={MINE_SCOPE_OPTIONS}
          onChange={(value) => {
            setMineScope(value)
            // 换范围回第 1 页：否则会停在"我受理的只有 3 条、当前是第 5 页"的空页上
            setCurrent(1)
          }}
          style={{ marginBottom: 16 }}
        />
      )}

      <Table<WorkOrderRow>
        rowKey="id"
        loading={loading}
        dataSource={rows}
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
          { title: '标题', dataIndex: 'title', ellipsis: true },
          {
            title: '类型',
            dataIndex: 'type',
            width: 90,
            render: (value: WorkOrderRow['type']) => <TypeTag type={value} />,
          },
          {
            title: '优先级',
            dataIndex: 'priority',
            width: 90,
            render: (value: WorkOrderRow['priority']) => <PriorityTag priority={value} />,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 160,
            render: (_: unknown, record) => (
              <Space size={4} wrap>
                <StatusTag status={record.status} />
                {/* 自助解决记录复用「已解决」状态，靠这个标记与人工结单区分 */}
                {record.selfResolved && <Tag color="cyan">自助解决</Tag>}
                {/* 取消申请复用“已受理”这个状态，这里单独标出来，
                    否则列表上看不出它正在等谁处理 */}
                {record.cancelRequestedAt && <Tag color="warning">取消待确认</Tag>}
              </Space>
            ),
          },
          {
            title: '创建人',
            dataIndex: 'createBy',
            width: 140,
            render: (_: unknown, record) => <UserCell user={record.createBy} />,
          },
          {
            title: '受理人',
            dataIndex: 'assigneeId',
            width: 140,
            render: (_: unknown, record) => <UserCell user={record.assigneeId} />,
          },
          {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 170,
            render: (value: unknown) => <Text type="secondary">{formatDateTime(value)}</Text>,
          },
          {
            title: 'AI 会话',
            dataIndex: 'aiConversationId',
            width: 120,
            render: (value: string | null) =>
              value ? <Text type="secondary">有</Text> : <Text type="secondary">-</Text>,
          },
          {
            title: '操作',
            width: 240,
            fixed: 'right',
            render: (_, record) => (
              <Space size={0} wrap>
                <Auth code={WORK_ORDER_PERMISSIONS.detail}>
                  <Button type="link" size="small" onClick={() => void openDetailById(record.id)}>
                    详情
                  </Button>
                </Auth>
                {/* 只有"待受理且没人认领"的单子能接。二次确认是为了防误点 */}
                {record.status === 'NEW' && record.assigneeId === null && (
                  <Auth code={WORK_ORDER_PERMISSIONS.claim}>
                    <Popconfirm
                      title="确认接取这张工单？"
                      description="接取后你将成为受理人，状态变为「已受理」。"
                      okText="接取"
                      cancelText="取消"
                      onConfirm={() => void onClaim(record)}
                    >
                      <Button type="link" size="small">
                        接取
                      </Button>
                    </Popconfirm>
                  </Auth>
                )}
                {/* 有待确认的取消申请时不允许改派：申请是发给当前受理人的，
                    换人之后这次申请就没有明确的处理人了 */}
                {(record.status === 'NEW' || record.status === 'ACCEPTED') &&
                  !hasCancelRequest(record) && (
                    <Auth code={WORK_ORDER_PERMISSIONS.assign}>
                      <Button type="link" size="small" onClick={() => void openDispatch(record)}>
                        {record.assigneeId === null ? '派发' : '改派'}
                      </Button>
                    </Auth>
                  )}
                {canResolve(record) && (
                  <Auth code={WORK_ORDER_PERMISSIONS.resolve}>
                    <Button type="link" size="small" onClick={() => openResolve(record)}>
                      结单
                    </Button>
                  </Auth>
                )}
                {canCancel(record) && (
                  <Auth code={WORK_ORDER_PERMISSIONS.cancel}>
                    <Popconfirm
                      title={record.status === 'NEW' ? '确认取消这张工单？' : '确认申请取消？'}
                      description={cancelDescription(record)}
                      okText="确认"
                      cancelText="返回"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => void onCancel(record)}
                    >
                      <Button type="link" size="small" danger>
                        取消
                      </Button>
                    </Popconfirm>
                  </Auth>
                )}
                {/* 取消申请只能由当前受理人处理。同意是关闭工单，拒绝则回到正常处理 */}
                {canDecideCancel(record) && (
                  <Auth code={WORK_ORDER_PERMISSIONS.cancelDecision}>
                    <Popconfirm
                      title="同意取消这张工单？"
                      description="同意后工单状态变为「已关闭」。"
                      okText="同意"
                      cancelText="返回"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => void onDecideCancel(record, true)}
                    >
                      <Button type="link" size="small" danger>
                        同意取消
                      </Button>
                    </Popconfirm>
                    <Popconfirm
                      title="拒绝这次取消申请？"
                      description="拒绝后工单保持「已受理」，由你继续处理。"
                      okText="拒绝"
                      cancelText="返回"
                      onConfirm={() => void onDecideCancel(record, false)}
                    >
                      <Button type="link" size="small">
                        拒绝
                      </Button>
                    </Popconfirm>
                  </Auth>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Drawer
        title="工单详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        // 窄屏铺满、宽屏固定宽度。用 CSS 的 min() 而不是 useBreakpoint：
        // 这里只需要一个宽度，为此引入一套断点监听不划算
        width="min(720px, 100%)"
        destroyOnHidden
      >
        {detailLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : detail === null ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有取到详情" />
        ) : (
          <Descriptions
            bordered
            size="small"
            column={{ xs: 1, sm: 1, md: 2 }}
            items={[
              { key: 'title', label: '标题', span: 2, children: detail.title },
              { key: 'type', label: '类型', children: <TypeTag type={detail.type} /> },
              { key: 'priority', label: '优先级', children: <PriorityTag priority={detail.priority} /> },
              {
                key: 'status',
                label: '状态',
                children: (
                  <Space size={4} wrap>
                    <StatusTag status={detail.status} />
                    {detail.selfResolved && <Tag color="cyan">自助解决</Tag>}
                    {detail.cancelRequestedAt && <Tag color="warning">取消待确认</Tag>}
                  </Space>
                ),
              },
              { key: 'createBy', label: '创建人', children: <UserCell user={detail.createBy} /> },
              { key: 'assignee', label: '受理人', children: <UserCell user={detail.assigneeId} /> },
              { key: 'createTime', label: '创建时间', children: formatDateTime(detail.createTime) },
              ...(detail.cancelRequestedAt
                ? [
                    {
                      key: 'cancelRequestedAt',
                      label: '取消申请时间',
                      span: 2,
                      children: formatDateTime(detail.cancelRequestedAt),
                    },
                  ]
                : []),
              {
                key: 'aiOption',
                label: '选中选项',
                children: detail.aiSelectedOptionId === null ? '-' : `#${detail.aiSelectedOptionId}`,
              },
              {
                key: 'conversation',
                label: 'AI 会话 id',
                span: 2,
                children: detail.aiConversationId ? (
                  // 可复制：拿它去 spring_ai_chat_memory 查这次对话的原貌
                  <Text code copyable={{ text: detail.aiConversationId }}>
                    {detail.aiConversationId}
                  </Text>
                ) : (
                  <Text type="secondary">不是从 AI 对话创建的</Text>
                ),
              },
              {
                key: 'description',
                label: '工单描述',
                span: 2,
                children: <Paragraph style={{ margin: 0 }}>{detail.problemDescription}</Paragraph>,
              },
              {
                key: 'original',
                label: '原始描述',
                span: 2,
                children: <OriginalRounds text={detail.originalProblemDescription} />,
              },
              {
                key: 'assigneeNote',
                label: '本次心得',
                span: 2,
                children: (
                  <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                    {detail.assigneeNote || '-'}
                  </Paragraph>
                ),
              },
            ]}
          />
        )}

        {/* 抽屉里也放一份操作入口：从消息中心点进详情后，不用先关抽屉再回表格找按钮 */}
        {detail && hasCancelRequest(detail) && (
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 16 }}
            message="提单人申请取消这张工单"
            description="同意后工单状态变为「已关闭」；拒绝后工单保持「已受理」，由受理人继续处理。"
          />
        )}
        {detail && (canResolve(detail) || canCancel(detail) || canDecideCancel(detail)) && (
          <Space wrap style={{ marginTop: 16 }}>
            {canResolve(detail) && (
              <Auth code={WORK_ORDER_PERMISSIONS.resolve}>
                <Button type="primary" onClick={() => openResolve(detail)}>
                  结单
                </Button>
              </Auth>
            )}
            {canCancel(detail) && (
              <Auth code={WORK_ORDER_PERMISSIONS.cancel}>
                <Popconfirm
                  title={detail.status === 'NEW' ? '确认取消这张工单？' : '确认申请取消？'}
                  description={cancelDescription(detail)}
                  okText="确认"
                  cancelText="返回"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => void onCancel(detail)}
                >
                  <Button danger>取消工单</Button>
                </Popconfirm>
              </Auth>
            )}
            {canDecideCancel(detail) && (
              <Auth code={WORK_ORDER_PERMISSIONS.cancelDecision}>
                <Popconfirm
                  title="同意取消这张工单？"
                  description="同意后工单状态变为「已关闭」。"
                  okText="同意"
                  cancelText="返回"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => void onDecideCancel(detail, true)}
                >
                  <Button danger>同意取消</Button>
                </Popconfirm>
                <Popconfirm
                  title="拒绝这次取消申请？"
                  description="拒绝后工单保持「已受理」，由你继续处理。"
                  okText="拒绝"
                  cancelText="返回"
                  onConfirm={() => void onDecideCancel(detail, false)}
                >
                  <Button>拒绝</Button>
                </Popconfirm>
              </Auth>
            )}
          </Space>
        )}
      </Drawer>

      <ManualWorkOrderDrawer
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        onCreated={onManualCreated}
      />

      <Modal
        title={dispatchTarget?.assigneeId === null ? '派发工单' : '改派工单'}
        open={dispatchTarget !== null}
        okText={dispatchTarget?.assigneeId === null ? '派发' : '改派'}
        cancelText="取消"
        confirmLoading={dispatching}
        onOk={() => void onDispatch()}
        onCancel={() => setDispatchTarget(null)}
        destroyOnHidden
      >
        {dispatchTarget && (
          <div style={{ marginBottom: 12 }}>
            把「{dispatchTarget.title}」指定给：
          </div>
        )}

        {candidateError && (
          <Alert type="error" showIcon message={candidateError} style={{ marginBottom: 12 }} />
        )}

        <Select<string>
          showSearch
          allowClear
          loading={candidateLoading}
          value={selectedAssignee}
          onChange={setSelectedAssignee}
          placeholder="选择受理人"
          style={{ width: '100%' }}
          // 候选人最多 200 个，本地按 label 过滤即可；再多就该改成服务端搜索了
          optionFilterProp="label"
          options={candidates.map((user) => ({
            value: user.id,
            label: user.nickname ? `${user.nickname}（${user.username}）` : user.username,
          }))}
        />

        {!candidateLoading && !candidateError && candidates.length === 0 && (
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 12 }}
            message="没有可派发的人"
            description="候选人 = 持有「接取工单」权限的账号。请先在角色管理里给运维人员授权。"
          />
        )}
      </Modal>

      <Modal
        title="结单"
        open={resolveTarget !== null}
        okText="结单"
        cancelText="取消"
        confirmLoading={resolving}
        onOk={() => void onResolve()}
        onCancel={() => {
          if (!resolving) {
            setResolveTarget(null)
            setResolveNote('')
          }
        }}
        destroyOnHidden
      >
        {resolveTarget && (
          <div style={{ marginBottom: 12 }}>
            把「{resolveTarget.title}」标记为已解决。
          </div>
        )}
        <div style={{ marginBottom: 8 }}>本次心得（选填）</div>
        <Input.TextArea
          value={resolveNote}
          onChange={(event) => setResolveNote(event.target.value)}
          placeholder="记录处理过程、根因或后续注意事项"
          maxLength={1000}
          showCount
          autoSize={{ minRows: 3, maxRows: 6 }}
        />
      </Modal>
    </Card>
  )
}

/**
 * 取 `@User` 字段里可用的字符串 id。
 *
 * 解析成功时是 `{id, username}`；用户服务不可用时后端会降级成裸数字，而那个数字
 * 可能已经丢了雪花 id 的精度。所以只在对象形态下返回 id，数字一律视为“无法判断”。
 */
function resolvedUserId(user: WorkOrderRow['assigneeId'] | WorkOrderRow['createBy']): string | null {
  return user !== null && typeof user === 'object' ? user.id : null
}

/**
 * 原始描述：一行一轮。
 *
 * 拆开逐轮标号显示，而不是整段丢进一个段落里 —— "第几轮补充的"本身就是信息，
 * 混在一起看就丢了。空行跳过（写入时后端也会归一化，这是兜底）。
 */
function OriginalRounds({ text }: { text: string | null }) {
  const rounds = splitRounds(text).filter((line) => line.trim() !== '')
  if (rounds.length === 0) {
    return <Text type="secondary">-</Text>
  }
  return (
    <Space direction="vertical" size={2} style={{ width: '100%' }}>
      {rounds.map((line, index) => (
        <Text key={`${index}-${line}`}>
          <Text type="secondary">第 {index + 1} 轮：</Text>
          {line}
        </Text>
      ))}
    </Space>
  )
}
