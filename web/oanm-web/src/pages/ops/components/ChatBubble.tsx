import { useState } from 'react'

import { Alert, Avatar, Button, Image, Space, Tag, Typography, theme } from 'antd'
import type { GlobalToken } from 'antd'
import { ExclamationCircleOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons'

import type { IntentOption, KnowledgeSource, WorkOrderDraft } from '../../../api/ai'
import type { UploadedFile } from '../../../api/files'
import { IntentOptionCard } from './IntentOptionCard'
import { WorkOrderDraftCard } from './WorkOrderDraftCard'

const { Text, Link } = Typography

/** 少于这个步数不折叠 —— 三四步本来就放得下，藏起来只多一次点击 */
const COLLAPSE_THRESHOLD = 4

/**
 * 自助解决步骤。
 *
 * 模型是"一句一行"写出来的，原样摊开在手机上就是一屏半。这里排成有序列表，
 * 步骤多时默认只露出前两步，其余点开再看。
 */
/** 引用的一篇知识库文章。有 `onOpen` 才做成链接 —— 没权限或对不上原文时只显示标题 */
export interface SolutionSource extends KnowledgeSource {
  onOpen?: () => void
}

export interface SolutionSteps {
  /** 列表之前的一句引导（"可以先按下面这几步试试"），由模型按情况生成 */
  lead?: string
  /** 第一步之前的一句提醒（如风险提示），不参与编号 */
  note?: string
  /** 有序步骤，按顺序编号 */
  steps: string[]
  /** 引用的文章；非空时优先用它，忽略 `footnote`（模型自己写的出处行，没有 id） */
  sources?: SolutionSource[]
  /** 纯文本出处，模型自己写的那一行；只有没有 `sources` 时才显示 */
  footnote?: string
  /** 折叠时显示几步；不传 = 不折叠（流式输出期间就是全部展开） */
  collapsedCount?: number
}

/**
 * 气泡里装什么。**一种 kind 一种气泡**，渲染只在这一处 switch。
 *
 * 以后要加新气泡（比如「工具调用记录」「附件」），在这里加一个分支、
 * 在下面的 switch 里加一个 case 即可 —— 调用方和布局都不用动。
 */
export type BubbleContent =
  /**
   * 纯文字。用户自己说的话、AI 的追问都用它。
   * 用户的话可以带图片附件，缩略图排在文字下方 —— 两者属于同一条消息，
   * 拆成两个气泡反而会让人以为图片是另一轮发的。
   */
  | { kind: 'text'; text: string; attachments?: UploadedFile[] }
  /** 自助解决步骤：单条气泡里的有序列表，步骤多时默认折叠 */
  | ({ kind: 'steps' } & SolutionSteps)
  /** 一组意图选项卡片（自助决定时卡片上方还有解决步骤列表） */
  | {
      kind: 'options'
      options: IntentOption[]
      /** 卡片上方的说明文字（纯文本，带换行），如故障确认的那句提问；普通选项轮没有 */
      text?: string
      /** 卡片上方的自助解决步骤；决策卡片轮才有 */
      steps?: SolutionSteps
      /** 当前选中的那一条（没有就 null） */
      selectedId: number | null
      /** 整组卡片是否只读 */
      disabled: boolean
      /**
       * 卡片不是"从几个猜测里挑一个"：故障确认的"是哪种现象"用它，
       * 不显示编号和概率。
       */
      plainCards?: boolean
      /** 卡片下方的补充说明，比如"正在继续给出选项" */
      hint?: string
      onSelect: (option: IntentOption) => void
    }
  /** 还没产出内容：正在分析/正在打字 */
  | { kind: 'pending'; text: string }
  /** 需要用户注意的一条提示（解析失败、请求出错） */
  | { kind: 'notice'; level: 'warning' | 'error'; title: string; description?: string }
  /** 生成出来的工单：骨架 → 卡片 → 已创建，同一条气泡按状态变化 */
  | {
      kind: 'workorder'
      status: 'generating' | 'ready' | 'failed' | 'submitted'
      draft: WorkOrderDraft | null
      /** true = 自助解决记录（已解决 + 受理人是本人），不是待受理的工单 */
      selfResolved: boolean
      workOrderId: string | null
      error: string | null
      onOpen: () => void
      onView: () => void
    }

interface ChatBubbleProps {
  role: 'user' | 'ai'
  content: BubbleContent
  /**
   * 名字旁边的小标签，标记这条回答**由谁产出**。现在填模型名；
   * 以后人工介入时，同一位置换成坐席名（比如「人工坐席 · 张三」）即可，
   * 气泡本身不用改。空字符串 / 不传则不显示。
   */
  tag?: string
  /**
   * 同一位说话人连着的下一条：留着头像的位置对齐，但不重复头像和名字。
   * AI 的一句话一个气泡，连说三句时不该挂三遍「AI 助手」。
   */
  continuation?: boolean
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
 * 同一个人连着说的几句（{@link ChatBubbleProps.continuation}）只标一次 ——
 * 否则三句短话会挂三遍「AI 助手」，反而更像系统提示而不像在聊天。
 */
export function ChatBubble({ role, content, tag, continuation = false }: ChatBubbleProps) {
  const { token } = theme.useToken()
  const isUser = role === 'user'
  // 选项/工单是一组卡片，给它们更宽的位置；对话文字保持在易读的行宽内
  const wide = content.kind === 'options' || content.kind === 'workorder'
  // 用户说的是一段话，永远带底色；AI 只有纯文字/步骤/正在输入才需要底色 ——
  // 选项/工单本身就是卡片、提示本身就是 Alert，再套一层框会变成两层边框
  const boxed =
    isUser || content.kind === 'text' || content.kind === 'steps' || content.kind === 'pending'

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
          // 连着说的下一条只占位不画头像，气泡才和上一条左对齐
          visibility: continuation ? 'hidden' : undefined,
          background: isUser ? token.colorPrimary : token.colorFillSecondary,
          color: isUser ? token.colorTextLightSolid : token.colorTextSecondary,
        }}
      />
      <div style={{ maxWidth: wide ? '88%' : '76%', minWidth: 0 }}>
        {!continuation && (
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
        )}
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
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: content.attachments?.length ? 8 : 0,
          }}
        >
          {content.text !== '' && (
            <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{content.text}</div>
          )}
          {content.attachments !== undefined && content.attachments.length > 0 && (
            <Image.PreviewGroup>
              <Space size={6} wrap>
                {content.attachments.map((attachment) => (
                  <Image
                    // id 是库里的 UUID；兜底用 url，保证旧草稿里缺 id 也能正常渲染
                    key={attachment.id || attachment.url}
                    src={attachment.url}
                    alt={attachment.originalFilename}
                    width={72}
                    height={72}
                    style={{
                      objectFit: 'cover',
                      borderRadius: token.borderRadius,
                      // 用户气泡是主题色底，用半透明白描边在浅色/深色主题下都能看轮廓
                      border: '1px solid rgba(255, 255, 255, 0.35)',
                    }}
                  />
                ))}
              </Space>
            </Image.PreviewGroup>
          )}
        </div>
      )

    case 'steps':
      return (
        <StepsList
          lead={content.lead}
          note={content.note}
          steps={content.steps}
          footnote={content.footnote}
          collapsedCount={content.collapsedCount}
          token={token}
        />
      )

    case 'options':
      return (
        <Space direction="vertical" size={10} style={{ width: '100%' }}>
          {content.text !== undefined && content.text !== '' && (
            // 提问/说明：保留换行
            <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {content.text}
            </div>
          )}
          {content.steps !== undefined && <StepsList {...content.steps} token={token} />}
          {content.options.map((option, index) => (
            <IntentOptionCard
              // 选项的 id 每轮都从 1 开始，加上下标免得同一轮里重复时 key 撞车
              key={`${option.id}-${index}`}
              option={option}
              selected={content.selectedId === option.id}
              disabled={content.disabled}
              plain={content.plainCards === true}
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

    case 'workorder':
      return (
        <WorkOrderDraftCard
          status={content.status}
          draft={content.draft}
          selfResolved={content.selfResolved}
          workOrderId={content.workOrderId}
          error={content.error}
          onOpen={content.onOpen}
          onView={content.onView}
        />
      )
  }
}

/**
 * 有序步骤列表：提醒 → 编号步骤 → 出处。
 *
 * 折叠状态放在组件自己的 state 里：一轮对话的气泡按轮次下标做 key，
 * 流继续往里写内容时不会重新挂载，展开后不会被新 token 顶回去。
 */
function StepsList({
  lead,
  note,
  steps,
  sources,
  footnote,
  collapsedCount,
  token,
}: SolutionSteps & { token: GlobalToken }) {
  const [expanded, setExpanded] = useState(false)
  const limit = collapsedCount ?? steps.length
  // 步数本来就不多时不折叠：藏起一两条只换来一次多余点击
  const collapsible = limit > 0 && steps.length > COLLAPSE_THRESHOLD
  const visible = collapsible && !expanded ? steps.slice(0, limit) : steps
  const rest = steps.length - visible.length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {lead !== undefined && lead !== '' && (
        <div style={{ wordBreak: 'break-word' }}>{lead}</div>
      )}
      {note !== undefined && note !== '' && (
        <div style={{ display: 'flex', gap: 6, color: token.colorWarningText }}>
          <ExclamationCircleOutlined style={{ marginTop: 4, flexShrink: 0 }} />
          <span style={{ wordBreak: 'break-word' }}>{note}</span>
        </div>
      )}
      {steps.length > 0 && (
        <ol
          style={{
            margin: 0,
            padding: 0,
            listStyle: 'none',
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          {visible.map((step, index) => (
            // 步骤是模型给的短句，没有稳定身份；顺序就是身份
            <li key={index} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
              <span
                aria-hidden
                style={{
                  flexShrink: 0,
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: token.colorFillSecondary,
                  color: token.colorTextSecondary,
                  fontSize: 11,
                  lineHeight: '18px',
                  textAlign: 'center',
                }}
              >
                {index + 1}
              </span>
              <span style={{ wordBreak: 'break-word' }}>{step}</span>
            </li>
          ))}
        </ol>
      )}
      {collapsible && (
        <Button
          type="link"
          size="small"
          onClick={() => setExpanded((value) => !value)}
          style={{ padding: 0, alignSelf: 'flex-start', height: 'auto' }}
        >
          {expanded ? '收起' : `展开查看全部（还有 ${rest} 步）`}
        </Button>
      )}
      {sources !== undefined && sources.length > 0 ? (
        // 有结构化出处时以它为准：模型自己写的那行只有标题，点不开，显示两份反而累赘
        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          参考知识：
          {sources.map((source, index) => (
            <span key={`${source.articleId ?? 'unknown'}-${index}`}>
              {index > 0 && '、'}
              {source.onOpen ? (
                <Link onClick={source.onOpen} style={{ fontSize: token.fontSizeSM }}>
                  {source.title}
                </Link>
              ) : (
                source.title
              )}
            </span>
          ))}
        </Text>
      ) : (
        footnote !== undefined &&
        footnote !== '' && (
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            {footnote}
          </Text>
        )
      )}
    </div>
  )
}
