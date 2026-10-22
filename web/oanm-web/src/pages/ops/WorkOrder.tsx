import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
  Typography,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router'

import { AI_PERMISSIONS } from '../../api/ai'
import { listUsersByPermission } from '../../api/user'
import type { AdminUserItem } from '../../api/user'
import {
  assignWorkOrder,
  claimWorkOrder,
  formatDateTime,
  getWorkOrder,
  listWorkOrders,
  splitRounds,
  STATUS_OPTIONS,
  WORK_ORDER_PERMISSIONS,
} from '../../api/workOrder'
import type { WorkOrderDetail, WorkOrderRow, WorkOrderStatus } from '../../api/workOrder'
import { Auth } from '../../permission/Auth'
import { PriorityTag, StatusTag, TypeTag, UserCell } from './components/WorkOrderCells'

const { Text, Paragraph } = Typography

// Select 只认 {value,label}，元数据里还带着颜色，这里摘一下
const STATUS_SELECT_OPTIONS = STATUS_OPTIONS.map((item) => ({ value: item.value, label: item.label }))

/** 列表范围。'mine' 只换一个查询参数，用户 id 由后端从会话里取 */
type Scope = 'all' | 'mine'

const SCOPE_OPTIONS: { label: string; value: Scope }[] = [
  { label: '全部工单', value: 'all' },
  { label: '我的工单', value: 'mine' },
]

/**
 * 工单列表。
 *
 * ## 列表与详情是两个接口
 * 表格只取摘要（标题/类型/优先级/状态/创建人/时间），正文（描述、解决方法）由抽屉
 * 按 id 单独拉。原因是解决方法那一段是模型生成的长文，一页 10 行可能就有十几 KB，
 * 而它们要打开抽屉才看得到。
 *
 * ## 创建人这一列在演示什么
 * 库里只存了 `create_by` 的 id，是后端**序列化时**换成用户对象的。所以这一列同时也是
 * 一块探针：service-auth 不可用时它会降级成「已降级 + id」，而不是整页报错。
 *
 * ## 分页与筛选
 * 照仓库里其它列表页的写法：`onChange` 只改 state，重新拉取交给 `useEffect`；
 * 换搜索词/换状态都要回到第 1 页，否则会停在一个"总数为 0 的第 5 页"上。
 */
export default function WorkOrderPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const [rows, setRows] = useState<WorkOrderRow[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<WorkOrderStatus | undefined>(undefined)
  const [scope, setScope] = useState<Scope>('all')
  const [loading, setLoading] = useState(true)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detail, setDetail] = useState<WorkOrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()

  const [dispatchTarget, setDispatchTarget] = useState<WorkOrderRow | null>(null)
  const [candidates, setCandidates] = useState<AdminUserItem[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [candidateError, setCandidateError] = useState<string | null>(null)
  const [selectedAssignee, setSelectedAssignee] = useState<string | undefined>(undefined)
  const [dispatching, setDispatching] = useState(false)

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
          mine: scope === 'mine',
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

  /**
   * 支持从消息中心直接落到某一条工单：`?openId=<工单id>`。
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
    if (!openId) {
      return
    }
    const next = new URLSearchParams(searchParams)
    next.delete('openId')
    setSearchParams(next, { replace: true })
    // 这里的 setState 正是"用 effect 同步外部系统"那条规则允许的场景：抽屉该不该开
    // 由地址栏决定，而不是由某次点击决定。三个 setState 在一次事件里被 React 合并成
    // 一次渲染，所以它不会真的级联。
    // eslint-disable-next-line react/set-state-in-effect
    void openDetailById(openId)
  }, [searchParams, setSearchParams, openDetailById])

  return (
    <Card
      className="page-transition"
      title="工单管理"
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
          {/* 建单目前只有 AI 这条路径，所以按钮直接把人送到智能提单的第一步 */}
          <Auth code={AI_PERMISSIONS.intentInference}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/ops/ai/intent')}>
              新建工单
            </Button>
          </Auth>
        </Space>
      }
    >
      <Segmented<Scope>
        value={scope}
        options={SCOPE_OPTIONS}
        onChange={(value) => {
          setScope(value)
          // 换范围回第 1 页：否则会停在"我的工单只有 3 条、当前是第 5 页"的空页上
          setCurrent(1)
        }}
        style={{ marginBottom: 16 }}
      />

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
            width: 100,
            render: (value: WorkOrderRow['status']) => <StatusTag status={value} />,
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
            width: 180,
            fixed: 'right',
            render: (_, record) => (
              <Space size={0}>
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
                {(record.status === 'NEW' || record.status === 'ACCEPTED') && (
                  <Auth code={WORK_ORDER_PERMISSIONS.assign}>
                    <Button type="link" size="small" onClick={() => void openDispatch(record)}>
                      {record.assigneeId === null ? '派发' : '改派'}
                    </Button>
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
              { key: 'status', label: '状态', children: <StatusTag status={detail.status} /> },
              { key: 'createBy', label: '创建人', children: <UserCell user={detail.createBy} /> },
              { key: 'assignee', label: '受理人', children: <UserCell user={detail.assigneeId} /> },
              { key: 'createTime', label: '创建时间', children: formatDateTime(detail.createTime) },
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
                key: 'solution',
                label: '解决方法',
                span: 2,
                children: (
                  <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                    {detail.solutionDetail || '-'}
                  </Paragraph>
                ),
              },
            ]}
          />
        )}
      </Drawer>

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
    </Card>
  )
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
