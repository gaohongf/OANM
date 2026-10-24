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
  Typography,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'

import {
  deleteSoftware,
  findPlatform,
  listManageSoftware,
  listSoftwareCategories,
  PLATFORM_OPTIONS,
  setSoftwareEnabled,
  SOFTWARE_PERMISSIONS,
} from '../../api/software'
import type {
  SoftwareCategory,
  SoftwareManageRow,
  SoftwarePlatform,
} from '../../api/software'
import { formatDateTime } from '../../api/workOrder'
import { Auth } from '../../permission/Auth'
import { CategoryPanel } from './components/CategoryPanel'
import { SoftwareFormModal } from './components/SoftwareFormModal'
import { VersionPanel } from './components/VersionPanel'

const { Text } = Typography

const PLATFORM_SELECT_OPTIONS = PLATFORM_OPTIONS.map((option) => ({
  value: option.value,
  label: option.label,
}))

const ENABLED_SELECT_OPTIONS = [
  { value: 'enabled', label: '已启用' },
  { value: 'disabled', label: '已停用' },
] as const

type EnabledFilter = (typeof ENABLED_SELECT_OPTIONS)[number]['value']

/**
 * 软件管理：维护分类、软件与版本。
 *
 * ## 列表与下载页的计数口径不同
 * 这里显示全部未删除版本（含停用），下载页只算启用中的。同一个软件在两边的
 * 「版本数」可能不一样 —— 这是刻意的，不是数据漂移。
 *
 * ## 版本放在抽屉里
 * 一个软件可能有十几个版本，塞进主表格会让每一行都过高；版本面板按需加载，
 * 关闭时不请求。
 */
export default function SoftwareManage() {
  const { message } = AntdApp.useApp()

  const [rows, setRows] = useState<SoftwareManageRow[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [platform, setPlatform] = useState<SoftwarePlatform | undefined>(undefined)
  const [enabledFilter, setEnabledFilter] = useState<EnabledFilter | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [togglingId, setTogglingId] = useState<string | null>(null)

  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(false)

  const [formOpen, setFormOpen] = useState(false)
  const [editingSoftware, setEditingSoftware] = useState<SoftwareManageRow | null>(null)

  const [versionOpen, setVersionOpen] = useState(false)
  const [versionSoftware, setVersionSoftware] = useState<SoftwareManageRow | null>(null)

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await listManageSoftware({
          current,
          size,
          keyword: keyword.trim() || undefined,
          categoryId,
          platform,
          enabled: enabledFilter === undefined ? undefined : enabledFilter === 'enabled',
        })
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, categoryId, platform, enabledFilter, message],
  )

  const loadCategories = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setCategoriesLoading(true)
      }
      try {
        setCategories(await listSoftwareCategories())
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
    setEditingSoftware(null)
    setFormOpen(true)
  }

  const openEdit = (record: SoftwareManageRow) => {
    setEditingSoftware(record)
    setFormOpen(true)
  }

  const openVersions = (record: SoftwareManageRow) => {
    setVersionSoftware(record)
    setVersionOpen(true)
  }

  const onToggle = async (record: SoftwareManageRow) => {
    setTogglingId(record.id)
    try {
      await setSoftwareEnabled(record.id, !record.enabled)
      message.success(record.enabled ? '已停用' : '已启用')
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败')
    } finally {
      setTogglingId(null)
    }
  }

  const onDelete = async (record: SoftwareManageRow) => {
    try {
      await deleteSoftware(record.id)
      message.success('删除成功')
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  const resetToFirstPage = () => setCurrent(1)

  const softwareTab = (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="搜索软件名称或厂商"
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
          placeholder="全部平台"
          style={{ width: 140 }}
          value={platform}
          options={PLATFORM_SELECT_OPTIONS}
          onChange={(value: SoftwarePlatform | undefined) => {
            setPlatform(value)
            resetToFirstPage()
          }}
        />
        <Select
          allowClear
          placeholder="全部状态"
          style={{ width: 130 }}
          value={enabledFilter}
          options={[...ENABLED_SELECT_OPTIONS]}
          onChange={(value: EnabledFilter | undefined) => {
            setEnabledFilter(value)
            resetToFirstPage()
          }}
        />
        <Auth code={SOFTWARE_PERMISSIONS.create}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建软件
          </Button>
        </Auth>
      </Space>

      <Table<SoftwareManageRow>
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
            title: '软件',
            dataIndex: 'name',
            render: (_, record) => (
              <Space direction="vertical" size={0}>
                <Text strong>{record.name}</Text>
                {record.vendor ? <Text type="secondary">{record.vendor}</Text> : null}
              </Space>
            ),
          },
          {
            title: '分类',
            dataIndex: 'categoryName',
            width: 120,
            render: (name: string | null) => name || '-',
          },
          {
            title: '最新版本',
            dataIndex: 'latestVersionNo',
            width: 120,
            render: (versionNo: string | null) => versionNo || '-',
          },
          {
            title: '适用平台',
            dataIndex: 'platforms',
            width: 200,
            render: (platforms: SoftwarePlatform[]) =>
              platforms.length === 0
                ? '-'
                : platforms.map((value) => {
                    const option = findPlatform(value)
                    return (
                      <Tag key={value} color={option?.color}>
                        {option?.label ?? value}
                      </Tag>
                    )
                  }),
          },
          {
            title: '版本数',
            width: 110,
            render: (_, record) => (
              <Text>
                {record.enabledVersionCount} / {record.versionCount}
              </Text>
            ),
          },
          { title: '总下载', dataIndex: 'downloadCount', width: 100 },
          {
            title: '状态',
            dataIndex: 'enabled',
            width: 90,
            render: (enabled: boolean) => (
              <Tag color={enabled ? 'success' : 'default'}>{enabled ? '已启用' : '已停用'}</Tag>
            ),
          },
          {
            title: '更新时间',
            dataIndex: 'updateTime',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
          },
          {
            title: '操作',
            key: 'actions',
            width: 260,
            render: (_, record) => (
              <Space>
                <Auth code={SOFTWARE_PERMISSIONS.manageDetail}>
                  <Button type="link" size="small" onClick={() => openVersions(record)}>
                    版本
                  </Button>
                </Auth>
                <Auth code={SOFTWARE_PERMISSIONS.update}>
                  <Button type="link" size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={SOFTWARE_PERMISSIONS.toggle}>
                  <Button
                    type="link"
                    size="small"
                    loading={togglingId === record.id}
                    onClick={() => void onToggle(record)}
                  >
                    {record.enabled ? '停用' : '启用'}
                  </Button>
                </Auth>
                <Auth code={SOFTWARE_PERMISSIONS.remove}>
                  <Popconfirm
                    title={`确定删除 ${record.name}？`}
                    description="会同时删除它的全部版本记录；已上传的文件不会被清理。"
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
      title="软件管理"
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
          { key: 'software', label: '软件', children: softwareTab },
          {
            key: 'category',
            label: '分类',
            children: (
              <CategoryPanel
                categories={categories}
                loading={categoriesLoading}
                onChanged={() => void loadCategories()}
              />
            ),
          },
        ]}
      />

      <SoftwareFormModal
        open={formOpen}
        editing={editingSoftware}
        categories={categories}
        onCancel={() => setFormOpen(false)}
        onSuccess={() => {
          setFormOpen(false)
          void load()
          // 编辑不会改分类，但新建可能发生在分类刚变更后；一并刷新，保证筛选下拉是最新的
          void loadCategories({ silent: true })
        }}
      />

      <VersionPanel
        open={versionOpen}
        software={versionSoftware}
        onClose={() => setVersionOpen(false)}
        onChanged={() => void load({ silent: true })}
      />
    </Card>
  )
}
