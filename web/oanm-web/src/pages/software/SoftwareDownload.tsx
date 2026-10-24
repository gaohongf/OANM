import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'

import {
  downloadSoftwareVersion,
  findPlatform,
  listSoftware,
  listSoftwareCategories,
  PLATFORM_OPTIONS,
} from '../../api/software'
import type { SoftwareCategory, SoftwarePlatform, SoftwareRow } from '../../api/software'
import { formatDateTime } from '../../api/workOrder'
import { SoftwareDetailDrawer } from './components/SoftwareDetailDrawer'
import { triggerDownload } from './download'

const { Text } = Typography

const PLATFORM_SELECT_OPTIONS = PLATFORM_OPTIONS.map((option) => ({
  value: option.value,
  label: option.label,
}))

/**
 * 自助软件下载。
 *
 * ## 列表只给摘要
 * 版本详情（说明、文件名、历史版本）在抽屉里按 id 单独取。一页软件可能有几十个版本，
 * 全塞进列表响应没有必要，抽屉关闭时也没人看。
 *
 * ## 「下载最新版」直接用列表里的 latestVersionId
 * 列表接口已经把最新启用版本算好了，不必先拉详情再下载；详情抽屉才用于挑历史版本。
 *
 * ## 下载后刷新
 * 计数在服务端原子自增，前端拿不到新值。下载成功后静默重拉当前页，
 * 让「总下载」和抽屉里的「下载次数」都显示真实数字。
 */
export default function SoftwareDownload() {
  const { message } = AntdApp.useApp()

  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [rows, setRows] = useState<SoftwareRow[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [platform, setPlatform] = useState<SoftwarePlatform | undefined>(undefined)
  const [loading, setLoading] = useState(true)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await listSoftware({
          current,
          size,
          // 空串不要发过去 —— 后端会把它当成一个真的搜索词
          keyword: keyword.trim() || undefined,
          categoryId,
          platform,
        })
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, categoryId, platform, message],
  )

  const loadCategories = useCallback(async () => {
    try {
      setCategories(await listSoftwareCategories())
    } catch {
      // 分类只是筛选条件，加载失败不阻塞软件列表；下拉为空即可
    }
  }, [])

  useEffect(() => {
    // 理由同其他列表页：挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect
    void loadCategories()
  }, [loadCategories])

  const onDownloadLatest = async (record: SoftwareRow) => {
    if (!record.latestVersionId) {
      message.warning('该软件还没有可下载的版本')
      return
    }
    setDownloadingId(record.latestVersionId)
    try {
      triggerDownload(await downloadSoftwareVersion(record.latestVersionId))
      message.success('已开始下载')
      await load({ silent: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下载失败')
    } finally {
      setDownloadingId(null)
    }
  }

  const openDetail = (record: SoftwareRow) => {
    setDetailId(record.id)
    setDrawerOpen(true)
  }

  const onSearch = (value: string) => {
    setKeyword(value)
    // 换搜索词要回到第 1 页，否则可能停在"总数为 0 的第 5 页"
    setCurrent(1)
  }

  return (
    <Card
      title="软件下载"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="搜索软件名称或厂商"
          enterButton={<SearchOutlined />}
          onSearch={onSearch}
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
            setCurrent(1)
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
            setCurrent(1)
          }}
        />
      </Space>

      <Table<SoftwareRow>
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
            render: (versionNo: string | null) =>
              versionNo ? <Tag color="success">{versionNo}</Tag> : '-',
          },
          {
            title: '适用平台',
            dataIndex: 'platforms',
            width: 220,
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
          { title: '版本数', dataIndex: 'versionCount', width: 90 },
          { title: '总下载', dataIndex: 'downloadCount', width: 100 },
          {
            title: '更新时间',
            dataIndex: 'updateTime',
            width: 180,
            render: (value: string | null) => formatDateTime(value),
          },
          {
            title: '操作',
            key: 'actions',
            width: 200,
            render: (_, record) => (
              <Space>
                <Button
                  type="link"
                  size="small"
                  icon={<DownloadOutlined />}
                  disabled={!record.latestVersionId}
                  loading={
                    record.latestVersionId !== null &&
                    downloadingId === record.latestVersionId
                  }
                  onClick={() => void onDownloadLatest(record)}
                >
                  下载最新版
                </Button>
                <Button type="link" size="small" onClick={() => openDetail(record)}>
                  详情
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <SoftwareDetailDrawer
        open={drawerOpen}
        softwareId={detailId}
        onClose={() => setDrawerOpen(false)}
        onDownloaded={() => void load({ silent: true })}
      />
    </Card>
  )
}
