import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  LoadingOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router'

import {
  changeKnowledgeArticleStatus,
  deleteKnowledgeArticle,
  KNOWLEDGE_PERMISSIONS,
  listKnowledgeCategories,
  listManageKnowledge,
  reindexKnowledgeArticle,
} from '../../api/knowledge'
import type {
  ArticleStatus,
  IndexStatus,
  KnowledgeCategory,
  KnowledgeManageRow,
} from '../../api/knowledge'
import { formatDateTime } from '../../api/workOrder'
import { Auth } from '../../permission/Auth'
import { CategoryPanel } from './components/CategoryPanel'
import { RevisionPanel } from './components/RevisionPanel'

const { Text } = Typography

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
] as const

type StatusFilter = (typeof STATUS_OPTIONS)[number]['value']

const STATUS_META: Record<ArticleStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'success' },
}

const INDEX_STATUS_META: Record<IndexStatus, { label: string; color: string }> = {
  NONE: { label: '未索引', color: 'default' },
  INDEXING: { label: '索引中', color: 'processing' },
  READY: { label: '就绪', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
}

/**
 * 知识管理：文章与分类的维护入口。
 *
 * ## 新建/编辑跳到独立页面
 * 正文是整篇 Markdown，弹窗里点到遮罩就会丢内容；编辑页见 `KnowledgeEditor.tsx`，
 * 由 `App.tsx` 静态挂载在 `/knowledge/manage/editor`。
 *
 * ## 索引状态是异步的
 * 保存/发布/回滚后接口只把状态置为「索引中」，真正的嵌入在后台线程池里跑。
 * 前端不轮询：列表刷新或下次操作时看到的就是最新状态，失败原因通过 Tooltip 展示。
 *
 * ## 重建索引只对已发布文章显示
 * 草稿本来就不该有索引，后端对草稿调用重建只会清掉残留；按钮藏起来避免误解。
 *
 * ## 分类与文章共用一份分类数据
 * 筛选下拉、表单下拉、分类维护页都用外层同一份，分类增删改后一起刷新。
 */
export default function KnowledgeManage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const [rows, setRows] = useState<KnowledgeManageRow[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [statusFilter, setStatusFilter] = useState<StatusFilter | undefined>(undefined)
  const [loading, setLoading] = useState(true)

  const [categories, setCategories] = useState<KnowledgeCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(false)

  const [revisionOpen, setRevisionOpen] = useState(false)
  const [revisionArticle, setRevisionArticle] = useState<KnowledgeManageRow | null>(null)

  const [statusChangingId, setStatusChangingId] = useState<string | null>(null)
  const [reindexingId, setReindexingId] = useState<string | null>(null)

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await listManageKnowledge({
          current,
          size,
          keyword: keyword.trim() || undefined,
          categoryId,
          status: statusFilter,
        })
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, categoryId, statusFilter, message],
  )

  const loadCategories = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setCategoriesLoading(true)
      }
      try {
        setCategories(await listKnowledgeCategories())
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载分类失败')
      } finally {
        setCategoriesLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    // 理由同其他列表页：挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect
    void loadCategories({ silent: true })
  }, [loadCategories])

  const openCreate = () => {
    navigate('/knowledge/manage/editor')
  }

  const openEdit = (record: KnowledgeManageRow) => {
    navigate(`/knowledge/manage/editor?id=${encodeURIComponent(record.id)}`)
  }

  const openRevisions = (record: KnowledgeManageRow) => {
    setRevisionArticle(record)
    setRevisionOpen(true)
  }

  const resetToFirstPage = () => setCurrent(1)

  const onToggleStatus = async (record: KnowledgeManageRow) => {
    const next: ArticleStatus = record.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
    setStatusChangingId(record.id)
    try {
      await changeKnowledgeArticleStatus(record.id, next)
      message.success(
        next === 'PUBLISHED' ? '已发布，正在后台建立索引' : '已取消发布，已从检索中移除',
      )
      await load({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败')
    } finally {
      setStatusChangingId(null)
    }
  }

  const onReindex = async (record: KnowledgeManageRow) => {
    setReindexingId(record.id)
    try {
      await reindexKnowledgeArticle(record.id)
      message.success('已提交重建，索引状态稍后刷新')
      await load({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重建索引失败')
    } finally {
      setReindexingId(null)
    }
  }

  const onDelete = async (record: KnowledgeManageRow) => {
    try {
      await deleteKnowledgeArticle(record.id)
      message.success('删除成功')
      await load()
      // 已发布数会变
      void loadCategories({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  const renderIndexStatus = (status: IndexStatus, record: KnowledgeManageRow) => {
    const meta = INDEX_STATUS_META[status]
    const tag = (
      <Tag
        color={meta.color}
        icon={status === 'INDEXING' ? <LoadingOutlined spin /> : undefined}
      >
        {meta.label}
      </Tag>
    )
    if (status === 'FAILED') {
      return <Tooltip title={record.indexError || '未知原因'}>{tag}</Tooltip>
    }
    if (status === 'READY') {
      return <Tooltip title={`索引于 ${formatDateTime(record.indexedAt)}`}>{tag}</Tooltip>
    }
    return tag
  }

  const articleTab = (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="搜索标题、摘要或正文"
          enterButton={<SearchOutlined />}
          onSearch={(value) => {
            setKeyword(value)
            resetToFirstPage()
          }}
          style={{ width: 260 }}
        />
        <Select
          allowClear
          placeholder="全部分类"
          style={{ width: 160 }}
          value={categoryId}
          options={categories.map((category) => ({
            value: category.id,
            label: category.name,
          }))}
          onChange={(value: string | undefined) => {
            setCategoryId(value)
            resetToFirstPage()
          }}
        />
        <Select
          allowClear
          placeholder="全部状态"
          style={{ width: 130 }}
          value={statusFilter}
          options={[...STATUS_OPTIONS]}
          onChange={(value: StatusFilter | undefined) => {
            setStatusFilter(value)
            resetToFirstPage()
          }}
        />
        <Auth code={KNOWLEDGE_PERMISSIONS.create}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建文章
          </Button>
        </Auth>
      </Space>

      <Table<KnowledgeManageRow>
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
          {
            title: '标题',
            dataIndex: 'title',
            render: (_, record) => (
              <Space direction="vertical" size={0}>
                <Text strong>{record.title}</Text>
                {record.summary ? (
                  <Text type="secondary" ellipsis style={{ maxWidth: 420 }}>
                    {record.summary}
                  </Text>
                ) : null}
              </Space>
            ),
          },
          {
            title: '分类',
            dataIndex: 'categoryName',
            width: 130,
            render: (name: string | null) => name || '-',
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (status: ArticleStatus) => {
              const meta = STATUS_META[status]
              return <Tag color={meta.color}>{meta.label}</Tag>
            },
          },
          {
            title: '索引状态',
            dataIndex: 'indexStatus',
            width: 110,
            render: (status: IndexStatus, record) => renderIndexStatus(status, record),
          },
          { title: '浏览', dataIndex: 'viewCount', width: 90 },
          {
            title: '更新时间',
            dataIndex: 'updateTime',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
          },
          {
            title: '操作',
            key: 'actions',
            width: 360,
            render: (_, record) => (
              <Space wrap>
                <Auth code={KNOWLEDGE_PERMISSIONS.update}>
                  <Button type="link" size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={KNOWLEDGE_PERMISSIONS.changeStatus}>
                  <Button
                    type="link"
                    size="small"
                    loading={statusChangingId === record.id}
                    onClick={() => void onToggleStatus(record)}
                  >
                    {record.status === 'PUBLISHED' ? '取消发布' : '发布'}
                  </Button>
                </Auth>
                <Auth code={KNOWLEDGE_PERMISSIONS.revisions}>
                  <Button type="link" size="small" onClick={() => openRevisions(record)}>
                    修订历史
                  </Button>
                </Auth>
                {record.status === 'PUBLISHED' ? (
                  <Auth code={KNOWLEDGE_PERMISSIONS.reindex}>
                    <Button
                      type="link"
                      size="small"
                      loading={reindexingId === record.id}
                      onClick={() => void onReindex(record)}
                    >
                      重建索引
                    </Button>
                  </Auth>
                ) : null}
                <Auth code={KNOWLEDGE_PERMISSIONS.remove}>
                  <Popconfirm
                    title={`确定删除《${record.title}》？`}
                    description="文章、修订历史和语义索引都会被删除，且不可恢复。"
                    onConfirm={() => void onDelete(record)}
                  >
                    <Button type="link" danger size="small">
                      删除
                    </Button>
                  </Popconfirm>
                </Auth>
              </Space>
            ),
          },
        ]}
      />
    </>
  )

  return (
    <Card
      title="知识管理"
      extra={
        <Button
          icon={<ReloadOutlined />}
          onClick={() => {
            // 两个 Tab 共用这个按钮，一起刷新
            void load()
            void loadCategories()
          }}
        >
          刷新
        </Button>
      }
    >
      <Tabs
        items={[
          { key: 'article', label: '文章', children: articleTab },
          {
            key: 'category',
            label: '分类',
            children: (
              <CategoryPanel
                categories={categories}
                loading={categoriesLoading}
                onChanged={() => {
                  void loadCategories()
                  // 分类名会显示在文章列表里
                  void load({ silent: true })
                }}
              />
            ),
          },
        ]}
      />

      <RevisionPanel
        open={revisionOpen}
        article={revisionArticle}
        onClose={() => setRevisionOpen(false)}
        onRestored={() => void load({ silent: true })}
      />
    </Card>
  )
}
