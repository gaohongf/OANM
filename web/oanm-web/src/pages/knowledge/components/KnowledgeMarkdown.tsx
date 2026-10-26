import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

import './markdown.css'

interface KnowledgeMarkdownProps {
  content: string
}

/**
 * Markdown 正文渲染。
 *
 * ## 不渲染原始 HTML
 * react-markdown 默认把 HTML 当普通文本，这是刻意的 —— 加上 rehype-raw 会让
 * 文章作者（或任何能写文章的人）拥有注入脚本的能力。
 *
 * ## 为什么包一层 div
 * react-markdown v10 不接受 className，样式只能挂在外层容器上。
 */
export function KnowledgeMarkdown({ content }: KnowledgeMarkdownProps) {
  return (
    <div className="knowledge-markdown">
      <Markdown remarkPlugins={[remarkGfm]}>{content}</Markdown>
    </div>
  )
}
