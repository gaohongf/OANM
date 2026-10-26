import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Drawer,
  Modal,
  Popconfirm,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'

import {
  getKnowledgeRevision,
  KNOWLEDGE_PERMISSIONS,
  listKnowledgeRevisions,
  restoreKnowledgeRevision,
} from '../../../api/knowledge'
import type {
  KnowledgeManageRow,
  KnowledgeRevision,
  KnowledgeRevisionDetail,
  RevisionChangeType,
} from '../../../api/knowledge'
import { formatDateTime } from '../../../api/workOrder'
import { Auth } from '../../../permission/Auth'
import { KnowledgeMarkdown } from './KnowledgeMarkdown'

const { Paragraph, Text } = Typography

const CHANGE_TYPE_META: Record<RevisionChangeType, { label: string; color: string }> = {
  CREATE: { label: '新建', color: 'blue' },
  UPDATE: { label: '编辑', color: 'green' },
  ROLLBACK: { label: '回滚', color: 'orange' },
}

interface RevisionPanelProps {
  open: boolean
  /** 为空时不请求 */
  article: KnowledgeManageRow | null
  onClose: () => void
  /** 回滚成功后通知外层刷新列表（正文、索引状态都可能变） */
  onRestored: () => void
}

/**
 * 修订历史。
 *
 * ## 每次保存一条完整快照
 * 因此修订号最大的那条就是当前文章。回滚不会修改历史行，而是追加一条
 * `ROLLBACK` 修订 —— 历史是只增不改的。
 *
 * ## 回滚不改变发布状态
 * 已发布的文章回滚后仍是已发布，并自动触发重建索引；草稿回滚后仍是草稿。
 */
export function RevisionPanel({ open, article, onClose, onRestored }: RevisionPanelProps) {
  const { message } = AntdApp.useApp()
  const [revisions, setRevisions] = useState<KnowledgeRevision[]>([])
  const [loading, setLoading] = useState(false)
  const [restoringId, setRestoringId] = useState<string | null>(null)

  const [preview, setPreview] = useState<KnowledgeRevisionDetail | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)

  const load = useCallback(async () => {
    if (!article) {
      return
    }
    setLoading(true)
    try {
      setRevisions(await listKnowledgeRevisions(article.id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载修订历史失败')
      setRevisions([])
    } finally {
      setLoading(false)
    }
  }, [article, message])

  useEffect(() => {
    if (open && article) {
      // eslint-disable-next-line react/set-state-in-effect
      void load()
    }
  }, [open, article, load])

  const openPreview = async (revision: KnowledgeRevision) => {
    if (!article) {
      return
    }
    setPreviewOpen(true)
    setPreview(null)
    setPreviewLoading(true)
    try {
      setPreview(await getKnowledgeRevision(article.id, revision.id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载修订失败')
      setPreviewOpen(false)
    } finally {
      setPreviewLoading(false)
    }
  }

  const onRestore = async (revision: KnowledgeRevision) => {
    if (!article) {
      return
    }
    setRestoringId(revision.id)
    try {
      await restoreKnowledgeRevision(article.id, revision.id)
      message.success(`已回滚到第 ${revision.revisionNo} 版`)
      await load()
      onRestored()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回滚失败')
    } finally {
      setRestoringId(null)
    }
  }

  return (
    <>
      <Drawer
        title={article ? `修订历史 · ${article.title}` : '修订历史'}
        open={open}
        onClose={onClose}
        width="min(860px, 100vw)"
        destroyOnHidden
      >
        <Table<KnowledgeRevision>
          rowKey="id"
          loading={loading}
          dataSource={revisions}
          pagination={false}
          scroll={{ x: 'max-content' }}
          columns={[
            {
              title: '修订',
              dataIndex: 'revisionNo',
              width: 90,
              render: (revisionNo: number) => <Text strong>#{revisionNo}</Text>,
            },
            {
              title: '类型',
              dataIndex: 'changeType',
              width: 100,
              render: (changeType: RevisionChangeType) => {
                const meta = CHANGE_TYPE_META[changeType]
                return <Tag color={meta.color}>{meta.label}</Tag>
              },
            },
            {
              title: '标题',
              dataIndex: 'title',
              ellipsis: true,
            },
            {
              title: '保存时间',
              dataIndex: 'createTime',
              width: 180,
              render: (value: string | null) => formatDateTime(value),
            },
            {
              title: '操作',
              key: 'actions',
              width: 160,
              render: (_, record) => (
                <Space>
                  <Auth code={KNOWLEDGE_PERMISSIONS.revisionDetail}>
                    <Button type="link" size="small" onClick={() => void openPreview(record)}>
                      查看
                    </Button>
                  </Auth>
                  <Auth code={KNOWLEDGE_PERMISSIONS.restore}>
                    <Popconfirm
                      title={`回滚到第 ${record.revisionNo} 版？`}
                      description="当前正文会变成该版本内容，并追加一条回滚记录；发布状态保持不变。"
                      onConfirm={() => void onRestore(record)}
                    >
                      <Button type="link" size="small" loading={restoringId === record.id}>
                        回滚
                      </Button>
                    </Popconfirm>
                  </Auth>
                </Space>
              ),
            },
          ]}
        />
      </Drawer>

      <Modal
        title={preview ? `第 ${preview.revisionNo} 版` : '修订预览'}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width="min(860px, 100vw)"
        destroyOnHidden
      >
        {previewLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : preview ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Text strong>{preview.title}</Text>
            {preview.summary ? (
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {preview.summary}
              </Paragraph>
            ) : null}
            <Text type="secondary">保存于 {formatDateTime(preview.createTime)}</Text>
            <KnowledgeMarkdown content={preview.content} />
          </Space>
        ) : null}
      </Modal>
    </>
  )
}
