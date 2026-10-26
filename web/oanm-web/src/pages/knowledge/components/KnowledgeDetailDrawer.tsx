import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Descriptions, Drawer, Empty, Skeleton, Space, Tag, Typography } from 'antd'

import { getKnowledgeArticle } from '../../../api/knowledge'
import type { KnowledgeDetail } from '../../../api/knowledge'
import { formatDateTime } from '../../../api/workOrder'
import { KnowledgeMarkdown } from './KnowledgeMarkdown'

const { Text } = Typography

interface KnowledgeDetailDrawerProps {
  open: boolean
  /** 为空时不请求；同时避免"打开过 A、再打开 B"时短暂显示 A 的旧数据 */
  articleId: string | null
  onClose: () => void
}

/**
 * 知识库文章详情。
 *
 * 详情接口每次调用都会把浏览次数 +1（仅限已发布文章），所以关闭抽屉后
 * 外层会静默重拉当前列表，让列表里的浏览数与抽屉一致。
 */
export function KnowledgeDetailDrawer({
  open,
  articleId,
  onClose,
}: KnowledgeDetailDrawerProps) {
  const { message } = AntdApp.useApp()
  const [detail, setDetail] = useState<KnowledgeDetail | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    if (!articleId) {
      return
    }
    setLoading(true)
    try {
      setDetail(await getKnowledgeArticle(articleId))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败')
      setDetail(null)
    } finally {
      setLoading(false)
    }
  }, [articleId, message])

  useEffect(() => {
    if (open && articleId) {
      // eslint-disable-next-line react/set-state-in-effect
      void load()
    }
  }, [open, articleId, load])

  return (
    <Drawer
      title={detail?.title ?? '文章详情'}
      open={open}
      onClose={onClose}
      width="min(960px, 100vw)"
      destroyOnHidden
    >
      {loading ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : !detail ? (
        <Empty description="暂无详情" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="分类">
              {detail.categoryName ? <Tag color="blue">{detail.categoryName}</Tag> : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="浏览">{detail.viewCount}</Descriptions.Item>
            <Descriptions.Item label="发布时间">{formatDateTime(detail.publishTime)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatDateTime(detail.updateTime)}</Descriptions.Item>
          </Descriptions>

          {detail.summary ? <Text type="secondary">{detail.summary}</Text> : null}

          <KnowledgeMarkdown content={detail.content} />
        </Space>
      )}
    </Drawer>
  )
}
