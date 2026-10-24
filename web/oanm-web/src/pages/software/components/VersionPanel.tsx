import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Drawer, Popconfirm, Space, Switch, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'

import {
  deleteSoftwareVersion,
  findPlatform,
  getManageSoftware,
  setSoftwareVersionEnabled,
  SOFTWARE_PERMISSIONS,
} from '../../../api/software'
import type {
  SoftwareManageDetail,
  SoftwareManageRow,
  SoftwareManageVersion,
} from '../../../api/software'
import { formatDateTime } from '../../../api/workOrder'
import { Auth } from '../../../permission/Auth'
import { formatFileSize } from '../format'
import { VersionFormModal } from './VersionFormModal'

const { Text } = Typography

interface VersionPanelProps {
  open: boolean
  software: SoftwareManageRow | null
  onClose: () => void
  /** 版本增删改后通知外层刷新软件列表（版本数、最新版本号会变） */
  onChanged: () => void
}

/**
 * 某个软件的全部版本（含停用）。
 *
 * ## 为什么单独拉详情
 * 管理列表不带版本明细；版本可能有很多，跟着列表走会让翻页变慢。
 * 打开抽屉时按 software.id 取一次，之后每次增删改都重拉。
 */
export function VersionPanel({ open, software, onClose, onChanged }: VersionPanelProps) {
  const { message } = AntdApp.useApp()
  const [detail, setDetail] = useState<SoftwareManageDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [togglingId, setTogglingId] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editingVersion, setEditingVersion] = useState<SoftwareManageVersion | null>(null)

  const load = useCallback(async () => {
    if (!software) {
      return
    }
    setLoading(true)
    try {
      setDetail(await getManageSoftware(software.id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载版本失败')
      setDetail(null)
    } finally {
      setLoading(false)
    }
  }, [software, message])

  useEffect(() => {
    if (open && software) {
      // eslint-disable-next-line react/set-state-in-effect
      void load()
    }
  }, [open, software, load])

  const afterChanged = async () => {
    await load()
    onChanged()
  }

  const onToggle = async (version: SoftwareManageVersion, enabled: boolean) => {
    setTogglingId(version.id)
    try {
      await setSoftwareVersionEnabled(version.id, enabled)
      message.success(enabled ? '已启用' : '已停用')
      await afterChanged()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败')
    } finally {
      setTogglingId(null)
    }
  }

  const onDelete = async (version: SoftwareManageVersion) => {
    try {
      await deleteSoftwareVersion(version.id)
      message.success('删除成功')
      await afterChanged()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  const openCreate = () => {
    setEditingVersion(null)
    setFormOpen(true)
  }

  const openEdit = (version: SoftwareManageVersion) => {
    setEditingVersion(version)
    setFormOpen(true)
  }

  return (
    <Drawer
      title={software ? `${software.name} · 版本管理` : '版本管理'}
      open={open}
      onClose={onClose}
      width="min(960px, 100vw)"
      destroyOnHidden
      extra={
        <Auth code={SOFTWARE_PERMISSIONS.versionCreate}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增版本
          </Button>
        </Auth>
      }
    >
      <Table<SoftwareManageVersion>
        rowKey="id"
        loading={loading}
        dataSource={detail?.versions ?? []}
        scroll={{ x: 'max-content' }}
        pagination={false}
        columns={[
          {
            title: '版本号',
            dataIndex: 'versionNo',
            width: 120,
            render: (versionNo: string) => <Text strong>{versionNo}</Text>,
          },
          {
            title: '平台',
            dataIndex: 'platform',
            width: 110,
            render: (value: string) => {
              const option = findPlatform(value)
              return <Tag color={option?.color}>{option?.label ?? value}</Tag>
            },
          },
          {
            title: '文件',
            dataIndex: 'fileName',
            render: (fileName: string | null, record) => (
              <Space direction="vertical" size={0}>
                <Text>{fileName || '-'}</Text>
                <Text type="secondary">{formatFileSize(record.fileSize)}</Text>
              </Space>
            ),
          },
          { title: '下载次数', dataIndex: 'downloadCount', width: 100 },
          {
            title: '状态',
            dataIndex: 'enabled',
            width: 100,
            render: (enabled: boolean, record) => (
              <Auth
                code={SOFTWARE_PERMISSIONS.versionToggle}
                fallback={<Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag>}
              >
                <Switch
                  size="small"
                  checked={enabled}
                  loading={togglingId === record.id}
                  onChange={(checked) => void onToggle(record, checked)}
                />
              </Auth>
            ),
          },
          {
            title: '版本说明',
            dataIndex: 'releaseNote',
            render: (note: string | null) => (
              <Text type="secondary" style={{ whiteSpace: 'pre-wrap' }}>
                {note || '-'}
              </Text>
            ),
          },
          {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 170,
            render: (value: string | null) => formatDateTime(value),
          },
          {
            title: '操作',
            key: 'actions',
            width: 140,
            render: (_, record) => (
              <Space>
                <Auth code={SOFTWARE_PERMISSIONS.versionUpdate}>
                  <Button type="link" size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={SOFTWARE_PERMISSIONS.versionRemove}>
                  <Popconfirm
                    title={`确定删除版本 ${record.versionNo}？`}
                    description="删除后下载页不再显示该版本；已上传的文件不会被清理。"
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

      <VersionFormModal
        open={formOpen}
        softwareId={software?.id ?? null}
        editing={editingVersion}
        onCancel={() => setFormOpen(false)}
        onSuccess={() => {
          setFormOpen(false)
          void afterChanged()
        }}
      />
    </Drawer>
  )
}
