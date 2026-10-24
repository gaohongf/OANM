import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Descriptions,
  Drawer,
  Empty,
  List,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd'
import { DownloadOutlined } from '@ant-design/icons'

import {
  downloadSoftwareVersion,
  getSoftwareDetail,
  PLATFORM_OPTIONS,
} from '../../../api/software'
import type { SoftwareDetail, SoftwareVersionRow } from '../../../api/software'
import { formatDateTime } from '../../../api/workOrder'
import { triggerDownload } from '../download'
import { formatFileSize } from '../format'

const { Text, Paragraph } = Typography

interface SoftwareDetailDrawerProps {
  open: boolean
  /** 为空时不请求；同时避免"打开过 A、再打开 B"时短暂显示 A 的旧数据 */
  softwareId: string | null
  onClose: () => void
  /** 下载成功后通知外层刷新列表里的下载次数 */
  onDownloaded: () => void
}

/**
 * 下载页的软件详情：简介 + 按平台分组的启用版本。
 *
 * ## 组内第一条是「最新」
 * 后端按创建时间倒序返回版本，不解析版本号语义（`2.0.10` 不会自动排在 `2.0.9` 后面）。
 * 这里沿用同一个顺序，第一条标「最新」，其余标「历史」—— 与后端口径一致，
 * 不会出现"列表说最新是 A、抽屉说 B"的错位。
 */
export function SoftwareDetailDrawer({
  open,
  softwareId,
  onClose,
  onDownloaded,
}: SoftwareDetailDrawerProps) {
  const { message } = AntdApp.useApp()
  const [detail, setDetail] = useState<SoftwareDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!softwareId) {
      return
    }
    setLoading(true)
    try {
      setDetail(await getSoftwareDetail(softwareId))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败')
      setDetail(null)
    } finally {
      setLoading(false)
    }
  }, [softwareId, message])

  useEffect(() => {
    if (open && softwareId) {
      // eslint-disable-next-line react/set-state-in-effect
      void load()
    }
  }, [open, softwareId, load])

  const onDownload = async (version: SoftwareVersionRow) => {
    setDownloadingId(version.id)
    try {
      triggerDownload(await downloadSoftwareVersion(version.id))
      message.success('已开始下载')
      // 计数在服务端，下载完重新拉一次，让它和列表都显示真实值
      await load()
      onDownloaded()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下载失败')
    } finally {
      setDownloadingId(null)
    }
  }

  // 只展示有版本的平台；平台顺序固定，避免同一份数据两次打开时排列不同
  const groups = PLATFORM_OPTIONS.map((option) => ({
    option,
    versions: detail?.versions.filter((version) => version.platform === option.value) ?? [],
  })).filter((group) => group.versions.length > 0)

  return (
    <Drawer
      title={detail?.name ?? '软件详情'}
      open={open}
      onClose={onClose}
      width="min(720px, 100vw)"
      destroyOnHidden
    >
      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : !detail ? (
        <Empty description="暂无详情" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="厂商">{detail.vendor || '-'}</Descriptions.Item>
            <Descriptions.Item label="分类">{detail.categoryName || '-'}</Descriptions.Item>
            <Descriptions.Item label="版本数">{detail.versionCount}</Descriptions.Item>
            <Descriptions.Item label="总下载次数">{detail.downloadCount}</Descriptions.Item>
            <Descriptions.Item label="更新时间" span={2}>
              {formatDateTime(detail.updateTime)}
            </Descriptions.Item>
          </Descriptions>

          {detail.description ? (
            <div>
              <Text strong>简介</Text>
              <Paragraph type="secondary" style={{ marginTop: 4, whiteSpace: 'pre-wrap' }}>
                {detail.description}
              </Paragraph>
            </div>
          ) : null}

          {groups.length === 0 ? (
            <Empty description="还没有可下载的版本" />
          ) : (
            groups.map(({ option, versions }) => (
              <div key={option.value}>
                <Space align="center" style={{ marginBottom: 4 }}>
                  <Tag color={option.color}>{option.label}</Tag>
                  <Text type="secondary">{versions.length} 个版本</Text>
                </Space>
                <List
                  size="small"
                  bordered
                  dataSource={versions}
                  renderItem={(version, index) => (
                    <List.Item
                      actions={[
                        <Button
                          key="download"
                          type="link"
                          size="small"
                          icon={<DownloadOutlined />}
                          loading={downloadingId === version.id}
                          onClick={() => void onDownload(version)}
                        >
                          下载
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        title={
                          <Space size={6}>
                            <Text strong>{version.versionNo}</Text>
                            {index === 0 ? <Tag color="success">最新</Tag> : <Tag>历史</Tag>}
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={2}>
                            {version.releaseNote ? (
                              <Text type="secondary" style={{ whiteSpace: 'pre-wrap' }}>
                                {version.releaseNote}
                              </Text>
                            ) : null}
                            <Text type="secondary">
                              {version.fileName ?? '-'} · {formatFileSize(version.fileSize)} ·
                              下载 {version.downloadCount ?? 0} 次 ·{' '}
                              {formatDateTime(version.createTime)}
                            </Text>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </div>
            ))
          )}
        </Space>
      )}
    </Drawer>
  )
}
