import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Select,
  Skeleton,
  Space,
  Table,
  Typography,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'

import { AI_PERMISSIONS } from '../../api/ai'
import {
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
  const [loading, setLoading] = useState(true)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detail, setDetail] = useState<WorkOrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

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
        })
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, status, message],
  )

  useEffect(() => {
    // 挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  const openDetail = async (row: WorkOrderRow) => {
    setDrawerOpen(true)
    setDetail(null)
    setDetailLoading(true)
    try {
      setDetail(await getWorkOrder(row.id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败')
      // 拉不到就关掉抽屉，而不是留一个空的骨架在转
      setDrawerOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

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
            width: 90,
            fixed: 'right',
            render: (_, record) => (
              <Auth code={WORK_ORDER_PERMISSIONS.detail}>
                <Button type="link" size="small" onClick={() => void openDetail(record)}>
                  详情
                </Button>
              </Auth>
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
