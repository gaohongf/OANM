import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Drawer, Empty, Skeleton, Tag } from 'antd'
import { DownOutlined, EyeOutlined, UpOutlined } from '@ant-design/icons'

import { getKnowledgeArticle } from '../../../api/knowledge'
import type { KnowledgeDetail } from '../../../api/knowledge'
import { formatDateTime } from '../../../api/workOrder'
import { KnowledgeMarkdown } from './KnowledgeMarkdown'

interface KnowledgeDetailDrawerProps {
  open: boolean
  /** 为空时不请求；同时避免"打开过 A、再打开 B"时短暂显示 A 的旧数据 */
  articleId: string | null
  onClose: () => void
  /** 详情接口返回的是自增后的浏览数，交给外层就地更新封面，不必重拉列表 */
  onViewed: (id: string, viewCount: number) => void
}

const NEW_WINDOW_DAYS = 7

/**
 * 知识库文章预览。
 *
 * 打开时只呈现"抽出一本书翻到扉页"的信息：分类、标题、时间、浏览数、摘要和标签。
 * 正文要点"阅读全文"才展开，避免长篇 Markdown 一上来就把抽屉撑成阅读器。
 *
 * 详情接口每次调用都会把浏览次数 +1（仅限已发布文章），所以关闭抽屉后
 * 外层会静默重拉当前列表，让封面上的浏览数与抽屉一致。
 */
export function KnowledgeDetailDrawer({
  open,
  articleId,
  onClose,
  onViewed,
}: KnowledgeDetailDrawerProps) {
  const { message } = AntdApp.useApp()
  const [detail, setDetail] = useState<KnowledgeDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [summaryExpanded, setSummaryExpanded] = useState(false)
  const [showFull, setShowFull] = useState(false)
  const [summaryNode, setSummaryNode] = useState<HTMLParagraphElement | null>(null)
  const [summaryOverflowing, setSummaryOverflowing] = useState(false)

  const load = useCallback(async () => {
    if (!articleId) {
      return
    }
    setLoading(true)
    try {
      const next = await getKnowledgeArticle(articleId)
      setDetail(next)
      onViewed(next.id, next.viewCount)
      // 换了一篇就从折叠状态重新开始（同一篇重开也一样）
      setSummaryExpanded(false)
      setShowFull(false)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载详情失败')
      setDetail(null)
    } finally {
      setLoading(false)
    }
  }, [articleId, message, onViewed])

  useEffect(() => {
    if (open && articleId) {
      // eslint-disable-next-line react/set-state-in-effect
      void load()
    }
  }, [open, articleId, load])

  // 摘要被 4 行裁掉的判断不能靠字数猜：抽屉宽度会变，同样字数可能一行也可能四行。
  // 真量一次 scrollHeight，比 clientHeight 大才给"展开"按钮；展开状态下不再量。
  useEffect(() => {
    if (!summaryNode) {
      return
    }
    const measure = () => {
      if (!summaryExpanded) {
        setSummaryOverflowing(summaryNode.scrollHeight > summaryNode.clientHeight + 1)
      }
    }
    const frame = window.requestAnimationFrame(measure)
    const observer = new ResizeObserver(measure)
    observer.observe(summaryNode)
    return () => {
      window.cancelAnimationFrame(frame)
      observer.disconnect()
    }
  }, [summaryNode, summaryExpanded, detail?.id])

  return (
    <Drawer
      title="文章预览"
      open={open}
      onClose={onClose}
      size="min(640px, 100vw)"
      rootClassName="knowledge-drawer"
      destroyOnHidden
    >
      {loading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : !detail ? (
        <Empty description="暂无详情" />
      ) : (
        <article className="knowledge-preview">
          <div className="preview-kicker">
            <span>{detail.categoryName ?? '未分类'}</span>
            <span className="preview-kicker-views">
              <EyeOutlined aria-hidden="true" /> {detail.viewCount} 次浏览
            </span>
          </div>

          <h2 className="preview-title">{detail.title}</h2>

          <div className="preview-meta">
            <span>更新于 {formatDateTime(detail.updateTime)}</span>
            {detail.publishTime ? <span>发布于 {formatDateTime(detail.publishTime)}</span> : null}
          </div>

          {detail.summary ? (
            <>
              <p
                ref={setSummaryNode}
                className={`preview-summary${summaryExpanded ? ' is-expanded' : ''}`}
              >
                {detail.summary}
              </p>
              {summaryOverflowing || summaryExpanded ? (
                <button
                  type="button"
                  className="preview-text-btn"
                  aria-expanded={summaryExpanded}
                  onClick={() => setSummaryExpanded((value) => !value)}
                >
                  {summaryExpanded ? '收起摘要' : '展开摘要'}
                </button>
              ) : null}
            </>
          ) : null}

          {/* 接口里没有自由标签字段，这里用分类和"最近更新"作为可用的标记 */}
          <div className="preview-tags" aria-label="标签">
            {detail.categoryName ? <Tag className="preview-tag">{detail.categoryName}</Tag> : null}
            {isRecent(detail.updateTime) ? (
              <Tag className="preview-tag preview-tag-brass">最近更新</Tag>
            ) : null}
          </div>

          <Button
            className="preview-read-btn"
            block
            icon={showFull ? <UpOutlined /> : <DownOutlined />}
            aria-expanded={showFull}
            onClick={() => setShowFull((value) => !value)}
          >
            {showFull ? '收起全文' : '阅读全文'}
          </Button>

          {showFull ? (
            <div className="preview-full">
              <KnowledgeMarkdown content={detail.content} />
            </div>
          ) : null}
        </article>
      )}
    </Drawer>
  )
}

/** 用日期字符串比较，避开不同浏览器对 "YYYY-MM-DD HH:mm:ss" 的解析差异 */
function isRecent(raw: string | null): boolean {
  const text = formatDateTime(raw)
  if (text === '-') {
    return false
  }
  return text.slice(0, 10) >= daysAgo(NEW_WINDOW_DAYS)
}

function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
