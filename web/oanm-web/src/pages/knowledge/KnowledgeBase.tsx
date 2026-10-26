import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'

import { listKnowledgeArticles, listKnowledgeCategories, searchKnowledge } from '../../api/knowledge'
import type {
  KnowledgeCategory,
  KnowledgeRow,
  KnowledgeSearchItem,
  SearchMode,
} from '../../api/knowledge'
import { formatDateTime } from '../../api/workOrder'
import { KnowledgeDetailDrawer } from './components/KnowledgeDetailDrawer'

const { Text } = Typography

/** 搜索不分页，直接取前 N 篇；后端还会再按 oanm.knowledge.search-limit 截断 */
const SEARCH_LIMIT = 20

/**
 * 知识库浏览与检索。
 *
 * ## 有没有关键字是两种查询
 * 不带关键字走分页浏览（按更新时间倒序）；带关键字走检索接口，语义优先、关键字补充，
 * 结果不分页、带相关度。两者的响应结构不同，所以列表数据分开存。
 *
 * ## mode 是后端告诉前端的"这次实际用了什么"
 * 语义检索依赖 Ollama，不可用时后端自动降级为关键字搜索，页面用黄色提示说明，
 * 而不是直接报错——降级后仍然能搜到东西。
 *
 * ## 详情会加浏览数
 * 详情接口每次调用都会 +1，关抽屉后静默重拉一次列表，让列表里的数字对得上。
 */
export default function KnowledgeBase() {
  const { message } = AntdApp.useApp()

  const [categories, setCategories] = useState<KnowledgeCategory[]>([])
  const [rows, setRows] = useState<KnowledgeRow[]>([])
  const [searchItems, setSearchItems] = useState<KnowledgeSearchItem[]>([])
  const [searchMode, setSearchMode] = useState<SearchMode | null>(null)
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(true)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)

  const isSearching = keyword.trim().length > 0

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const query = keyword.trim()
        if (query) {
          const result = await searchKnowledge({ q: query, limit: SEARCH_LIMIT, categoryId })
          setSearchItems(result.items)
          setSearchMode(result.mode)
          setRows([])
          setTotal(0)
        } else {
          const page = await listKnowledgeArticles({ current, size, categoryId })
          setRows(page.records)
          setTotal(page.total)
          setSearchItems([])
          setSearchMode(null)
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
        // 失败时不要留着上一次搜索的结果，否则看起来像"新查询命中了旧文章"
        setRows([])
        setTotal(0)
        setSearchItems([])
        setSearchMode(null)
      } finally {
        setLoading(false)
      }
    },
    [keyword, categoryId, current, size, message],
  )

  const loadCategories = useCallback(async () => {
    try {
      setCategories(await listKnowledgeCategories())
    } catch {
      // 分类只是筛选条件，加载失败不阻塞列表；下拉为空即可
    }
  }, [])

  useEffect(() => {
    // 挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect
    void loadCategories()
  }, [loadCategories])

  const openDetail = (id: string) => {
    setDetailId(id)
    setDrawerOpen(true)
  }

  const closeDetail = () => {
    setDrawerOpen(false)
    // 浏览数在服务端自增，前端拿不到新值
    void load({ silent: true })
  }

  const renderTitle = (record: KnowledgeRow | KnowledgeSearchItem) => (
    <Button
      type="link"
      size="small"
      style={{ padding: 0, height: 'auto', textAlign: 'left', whiteSpace: 'normal' }}
      onClick={() => openDetail(record.id)}
    >
      {record.title}
    </Button>
  )

  const searchColumns = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (_: unknown, record: KnowledgeSearchItem) => renderTitle(record),
    },
    { title: '摘要', dataIndex: 'summary', ellipsis: true, render: (value: string | null) => value || '-' },
    {
      title: '分类',
      dataIndex: 'categoryName',
      width: 140,
      render: (name: string | null) => name || '-',
    },
    {
      title: '相关度',
      dataIndex: 'score',
      width: 100,
      render: (score: number | null) =>
        score === null ? <Text type="secondary">-</Text> : score.toFixed(3),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | null) => formatDateTime(value),
    },
  ]

  return (
    <Card
      title="知识库"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="搜索故障现象、处理方法…"
          enterButton={<SearchOutlined />}
          onSearch={(value) => {
            setKeyword(value)
            // 换搜索词要回到第 1 页，否则可能停在"总数为 0 的第 5 页"
            setCurrent(1)
          }}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          placeholder="全部分类"
          style={{ width: 180 }}
          value={categoryId}
          options={categories.map((category) => ({
            value: category.id,
            label: category.name,
          }))}
          onChange={(value: string | undefined) => {
            setCategoryId(value)
            setCurrent(1)
          }}
        />
      </Space>

      {isSearching && searchMode !== null ? (
        searchMode === 'SEMANTIC' ? (
          <Space style={{ marginBottom: 12 }}>
            <Tag color="success">语义检索</Tag>
            <Text type="secondary">共找到 {searchItems.length} 篇</Text>
          </Space>
        ) : (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="语义检索暂不可用，已降级为关键字搜索"
          />
        )
      ) : null}

      {isSearching ? (
        <Table<KnowledgeSearchItem>
          rowKey="id"
          loading={loading}
          dataSource={searchItems}
          scroll={{ x: 'max-content' }}
          pagination={false}
          columns={searchColumns}
        />
      ) : (
        <Table<KnowledgeRow>
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
              render: (_: unknown, record: KnowledgeRow) => renderTitle(record),
            },
            {
              title: '摘要',
              dataIndex: 'summary',
              ellipsis: true,
              render: (value: string | null) => value || '-',
            },
            {
              title: '分类',
              dataIndex: 'categoryName',
              width: 140,
              render: (name: string | null) => name || '-',
            },
            {
              title: '更新时间',
              dataIndex: 'updateTime',
              width: 180,
              render: (value: string | null) => formatDateTime(value),
            },
            { title: '浏览', dataIndex: 'viewCount', width: 90 },
          ]}
        />
      )}

      <KnowledgeDetailDrawer
        open={drawerOpen}
        articleId={detailId}
        onClose={closeDetail}
      />
    </Card>
  )
}
