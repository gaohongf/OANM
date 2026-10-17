import { Alert, Avatar, Space, Tag, Typography, theme } from 'antd'
import type { GlobalToken } from 'antd'
import { RobotOutlined, UserOutlined } from '@ant-design/icons'

import type { IntentOption } from '../../../api/ai'
import { IntentOptionCard } from './IntentOptionCard'

const { Text } = Typography

/**
 * 气泡里装什么。**一种 kind 一种气泡**，渲染只在这一处 switch。
 *
 * 以后要加新气泡（比如「工具调用记录」「附件」），在这里加一个分支、
 * 在下面的 switch 里加一个 case 即可 —— 调用方和布局都不用动。
 */
export type BubbleContent =
  /** 纯文字。用户自己说的话、AI 的追问都用它 */
  | { kind: 'text'; text: string }
  /** 一组意图选项卡片 */
  | {
      kind: 'options'
      options: IntentOption[]
      /** 当前选中的那一条（没有就 null） */
      selectedId: number | null
      /** 整组卡片是否只读 */
      disabled: boolean
      /** 卡片下方的补充说明，比如"正在继续给出选项" */
      hint?: string
      onSelect: (option: IntentOption) => void
    }
  /** 还没产出内容：正在分析/正在打字 */
  | { kind: 'pending'; text: string }
  /** 需要用户注意的一条提示（解析失败、请求出错） */
  | { kind: 'notice'; level: 'warning' | 'error'; title: string; description?: string }

interface ChatBubbleProps {
  role: 'user' | 'ai'
  content: BubbleContent
  /**
   * 名字旁边的小标签，标记这条回答**由谁产出**。现在填模型名；
   * 以后人工介入时，同一位置换成坐席名（比如「人工坐席 · 张三」）即可，
   * 气泡本身不用改。空字符串 / 不传则不显示。
   */
  tag?: string
}

/**
 * 一条对话气泡。
 *
 * ## 为什么用户和 AI 的差别不止"左右"
 * 用户的话是**一团**，所以有底色、圆角、贴右；AI 的选项是一**组卡片**，
 * 再套一层底色只会显得脏，所以 options 气泡不带底色、也更宽。
 * 这样即便去掉头像，也能一眼分清"这是我说的"和"这是它回的"。
 *
 * ## 为什么每个气泡都带名字
 * 群里聊惯了就懂：头像只有一个图标，滚动时很容易丢失上下文。
 * 名字很小、很淡，但省掉了"这行是谁说的"这一问。
 */
export function ChatBubble({ role, content, tag }: ChatBubbleProps) {
  const { token } = theme.useToken()
  const isUser = role === 'user'
  // 选项是一组卡片，给它更宽的位置；对话文字保持在易读的行宽内
  const wide = content.kind === 'options'
  // 用户说的是一段话，永远带底色；AI 只有纯文字/正在输入才需要底色 ——
  // 选项本身就是卡片、提示本身就是 Alert，再套一层框会变成两层边框
  const boxed = isUser || content.kind === 'text' || content.kind === 'pending'

  return (
    <div
      className="page-transition"
      style={{
        display: 'flex',
        flexDirection: isUser ? 'row-reverse' : 'row',
        alignItems: 'flex-start',
        gap: 10,
      }}
    >
      <Avatar
        size={32}
        icon={isUser ? <UserOutlined /> : <RobotOutlined />}
        style={{
          flexShrink: 0,
          background: isUser ? token.colorPrimary : token.colorFillSecondary,
          color: isUser ? token.colorTextLightSolid : token.colorTextSecondary,
        }}
      />
      <div style={{ maxWidth: wide ? '88%' : '76%', minWidth: 0 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            marginBottom: 4,
            justifyContent: isUser ? 'flex-end' : 'flex-start',
          }}
        >
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            {isUser ? '你' : 'AI 助手'}
          </Text>
          {tag !== undefined && tag !== '' && (
            <Tag bordered={false} style={{ marginInlineEnd: 0, fontSize: token.fontSizeSM }}>
              {tag}
            </Tag>
          )}
        </div>
        <div
          style={
            boxed
              ? {
                  padding: '10px 14px',
                  background: isUser ? token.colorPrimary : token.colorFillQuaternary,
                  color: isUser ? token.colorTextLightSolid : undefined,
                  borderRadius: token.borderRadiusLG,
                  // 尖角必须落在靠近头像的那一侧，指回说话的人：
                  // 用户的头像是 row-reverse 排到右边的，所以尖角在右上；AI 在左上。
                  // 四个角都写死成具体值，不依赖 undefined 回落到简写 —— 那样容易看不出意图，
                  // 也容易被别处的样式盖掉
                  borderTopLeftRadius: isUser ? token.borderRadiusLG : 0,
                  borderTopRightRadius: isUser ? 0 : token.borderRadiusLG,
                }
              : undefined
          }
        >
          {renderContent(content, token)}
        </div>
      </div>
    </div>
  )
}

/** 各种气泡的正文。新增气泡类型时，改动集中在这里。 */
function renderContent(content: BubbleContent, token: GlobalToken) {
  switch (content.kind) {
    case 'text':
      // 这里不用 Typography.Text：它自带文字色，会把用户气泡的浅色文字盖回深色
      return (
        <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{content.text}</div>
      )

    case 'options':
      return (
        <Space direction="vertical" size={10} style={{ width: '100%' }}>
          {content.options.map((option, index) => (
            <IntentOptionCard
              // 选项的 id 每轮都从 1 开始，加上下标免得同一轮里重复时 key 撞车
              key={`${option.id}-${index}`}
              option={option}
              selected={content.selectedId === option.id}
              disabled={content.disabled}
              onSelect={content.onSelect}
            />
          ))}
          {content.hint !== undefined && (
            <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              {content.hint}
            </Text>
          )}
        </Space>
      )

    case 'pending':
      return (
        <Space size={8}>
          <span className="chat-typing" style={{ color: token.colorTextTertiary }}>
            <i />
            <i />
            <i />
          </span>
          <Text type="secondary">{content.text}</Text>
        </Space>
      )

    case 'notice':
      return (
        <Alert
          type={content.level}
          showIcon
          message={content.title}
          description={content.description}
        />
      )
  }
}
