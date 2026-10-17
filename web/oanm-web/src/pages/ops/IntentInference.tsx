import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import { App as AntdApp, Avatar, Button, Card, Flex, Input, Space, Typography, theme } from 'antd'
import { FileAddOutlined, ReloadOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'

import { AI_PERMISSIONS, startIntentInference } from '../../api/ai'
import type { IntentNode } from '../../api/ai'
import { clearDraft, emptyDraft, loadDraft, saveDraft } from '../../api/aiSession'
import type { IntentDraft, IntentRound } from '../../api/aiSession'
import { Auth } from '../../permission/Auth'
import { ChatBubble } from './components/ChatBubble'
import type { BubbleContent } from './components/ChatBubble'

const { Text, Paragraph } = Typography
const { TextArea } = Input

/**
 * 空白页上那块固定引导牌的文案（纯前端，不经过后端）。
 * 想调整"该怎么说"的建议时只改这里。
 */
const GUIDE_TIPS = [
  { label: '时间', hint: '什么时候开始的、持续了多久，比如「昨天上午十点」' },
  { label: '地点', hint: '哪台设备、哪个系统或哪间办公室，比如「行政楼三楼」「财务系统」' },
  { label: '现象', hint: '你看到了什么、做到哪一步出的错，比如「导出报表一直转圈」' },
]

/**
 * 智能提单第一步：意图推断。
 *
 * ## 交互形状
 * 一整段对话：用户说的话靠右，AI 的作答靠左。AI 这一轮可能给一组候选意图卡片，
 * 也可能（缺时间、地点这类关键信息时）反过来问一句 —— 两种形态都是"这一轮它说了什么"，
 * 所以都落在同一条气泡里，只是气泡类型不同（见 `ChatBubble` 的 `BubbleContent`）。
 *
 * 后端是一个工作流（判定 → 追问 / 选项），每条 `chat` 帧带着产出它的节点名
 * （见 `api/ai.ts` 的 `IntentNode`），第一个 `chat` 帧就说明了本轮是哪种。
 *
 * ## 为什么只保留一个输入框
 * "第一句描述"和"后续补充"对用户是同一件事，没必要换一个框、换一套按钮文案。
 * 只有 placeholder 和按钮文字会随上下文变（开始分析 / 继续分析 / 提交回答）。
 *
 * ## 为什么只有最新一轮的选项能点
 * 选项编号每轮都从 1 开始，而提交时只带编号。让用户选第二轮的 #2、却去提交第一轮的 #2，
 * 后端分不清指的是哪一条。历史轮次的卡片保留下来但只读，并标一句"仅供回看"。
 *
 * ## 状态的持久化
 * 草稿（每轮的输入 + 选项 + 选中的那张）落在 sessionStorage 里，见 `api/aiSession.ts`。
 * 它同时是下一步的输入：填单页的「原始描述」**以这里记录的用户原话为准**，
 * 而不是信模型转述的版本。
 */
export default function IntentInference() {
  const { message } = AntdApp.useApp()
  const { token } = theme.useToken()
  const navigate = useNavigate()

  // sessionStorage 是同步的，所以直接当 useState 的初始值 ——
  // 不需要"挂载后再 setState"的 effect（那个模式还会多渲染一帧空页面）
  const [draft, setDraft] = useState<IntentDraft>(() => loadDraft() ?? emptyDraft())
  const [input, setInput] = useState('')
  const [supplement, setSupplement] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [noOption, setNoOption] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  /** 上一帧的轮次数，用来判断"是不是用户刚发了新消息"（见下面的滚动 effect） */
  const lastRoundCount = useRef(0)

  // 每次变化都落盘：中途刷新页面时，前面几轮和会话 id 都还在
  useEffect(() => {
    saveDraft(draft)
  }, [draft])

  // 离开页面时掐掉流。不掐的话它会一直读到模型把话说完，
  // 期间还在往一个已经卸载的组件写状态（React 不会报错，但请求白白挂着）
  useEffect(() => {
    return () => abortRef.current?.abort()
  }, [])

  /**
   * 跟着最新内容滚到底。
   *
   * 两种情况下必须滚：用户自己刚发了一条；以及流还在往最后那条里加字。
   * 但用户往上翻历史的时候不要把他拽回来 —— 所以只有"本来就贴着底部"才跟随。
   */
  useEffect(() => {
    const el = scrollRef.current
    if (el === null) {
      return
    }
    const grew = draft.rounds.length !== lastRoundCount.current
    lastRoundCount.current = draft.rounds.length
    if (!grew && el.scrollHeight - el.scrollTop - el.clientHeight > 160) {
      return
    }
    el.scrollTop = el.scrollHeight
  }, [draft.rounds, streaming, error, noOption])

  /**
   * 改写**当前（最后一个）**轮次。流里的每个事件都落到它上面，所以收成一个函数。
   *
   * 用 `prev.rounds.length - 1` 而不是闭包里算好的下标：闭包里那个读的是发起时的
   * draft（已经过期了），"最后一个"则天然指向本轮。
   */
  const patchActiveRound = useCallback((patch: (round: IntentRound) => IntentRound) => {
    setDraft((prev) => {
      const last = prev.rounds.length - 1
      if (last < 0) {
        return prev
      }
      const rounds = [...prev.rounds]
      rounds[last] = patch(rounds[last])
      return { ...prev, rounds }
    })
  }, [])

  const run = useCallback(
    async (text: string) => {
      const controller = new AbortController()
      abortRef.current = controller
      const conversationId = draft.conversationId

      setStreaming(true)
      setError(null)
      setNoOption(false)
      // 先放一个占位轮次：选项会一条条填进来，而上一轮的卡片就此只读。
      // mode 先记 PENDING —— 此刻还不知道这轮会拿回选项还是追问
      setDraft((prev) => ({
        ...prev,
        // 新一轮意味着新的一组选项，上一轮选中的那条已经过期了
        selected: null,
        rounds: [
          ...prev.rounds,
          { input: text, mode: 'PENDING', model: '', question: '', options: [] },
        ],
      }))

      let received = 0
      /**
       * 本轮由哪个节点作答。**在闭包外面**记一份，因为下面 finally 里要拿它判断
       * "没解析出选项"到底是不是个错误 —— 那时 draft 已经不容易读了。
       */
      let mode: IntentNode | null = null
      try {
        await startIntentInference({
          conversationId,
          userInput: text,
          signal: controller.signal,
          // 首轮的 threadId 是后端生成的，就在这个事件里 ——
          // 它同时是下一轮和提交工单要带的会话标识
          onWorkflow: (threadId) => setDraft((prev) => ({ ...prev, conversationId: threadId })),
          // 模型名贴在气泡名字旁边；以后人工介入时这里记的是坐席
          onModel: (modelName) => patchActiveRound((round) => ({ ...round, model: modelName })),
          onMode: (node) => {
            mode = node
            patchActiveRound((round) => ({ ...round, mode: node }))
          },
          // 收到的每次都是完整一段（不是增量），直接覆盖
          onQuestion: (question) => patchActiveRound((round) => ({ ...round, question })),
          onOption: (option) => {
            received += 1
            patchActiveRound((round) => ({ ...round, options: [...round.options, option] }))
          },
          onError: (msg) => setError(msg),
        })
      } finally {
        setStreaming(false)
        abortRef.current = null
        // 中途自己中断的不算"没解析出选项"，别把它报成解析失败；
        // 追问那一轮本来就没有也不该有选项，一条都没解析出来是**正确**结果，更不能报格式错误
        setNoOption(!controller.signal.aborted && received === 0 && mode !== 'ask')
      }
    },
    [draft.conversationId, patchActiveRound],
  )

  /** 第一句描述和后续补充共用同一个输入框，只是数据源和按钮文案不同 */
  const isFirst = draft.rounds.length === 0
  const composerValue = isFirst ? input : supplement
  const setComposerValue = isFirst ? setInput : setSupplement
  const onSend = () => {
    const text = composerValue.trim()
    if (text === '' || streaming) {
      return
    }
    if (isFirst) {
      setInput('')
    } else {
      setSupplement('')
    }
    void run(text)
  }

  const onInterrupt = () => {
    abortRef.current?.abort()
    message.info('已中断本轮分析')
  }

  const onReset = () => {
    abortRef.current?.abort()
    clearDraft()
    setDraft(emptyDraft())
    setError(null)
    setNoOption(false)
  }

  const activeIndex = draft.rounds.length - 1
  const activeRound = activeIndex >= 0 ? draft.rounds[activeIndex] : null
  /** 当前这一轮 AI 在**追问**，而不是给选项。输入框的语义与文案都要跟着走 */
  const asking = activeRound?.mode === 'ask'
  const selectedHere =
    draft.selected !== null && draft.selected.roundIndex === activeIndex
      ? draft.selected.option
      : null

  const onCreateWorkOrder = () => {
    if (selectedHere === null || draft.conversationId === null) {
      return
    }
    // 会话 id 与分析结果都留在草稿里，下一步据此预填表单
    navigate(
      `/ops/ai/submit?conversationId=${encodeURIComponent(draft.conversationId)}&option=${selectedHere.id}`,
    )
  }

  /**
   * 一轮 AI 作答该渲染成哪种气泡。
   * 返回 null 表示这一轮没有可显示的内容（比如流在产出任何东西之前就被中断了）。
   */
  const aiBubble = (round: IntentRound, index: number): BubbleContent | null => {
    const isActive = index === activeIndex

    if (round.mode === 'PENDING') {
      // 节点还没定：这时候说"没给出选项"是错的，它还不知道自己要给什么
      return streaming && isActive ? { kind: 'pending', text: '正在分析…' } : null
    }

    if (round.mode === 'ask') {
      if (round.question !== '') {
        return { kind: 'text', text: round.question }
      }
      // 节点定了但第一个字还没到
      return streaming && isActive
        ? { kind: 'pending', text: '正在组织问题…' }
        : { kind: 'text', text: '（这一轮 AI 没有给出内容，可以在下面重新描述）' }
    }

    if (round.options.length === 0) {
      return streaming && isActive ? { kind: 'pending', text: '正在分析…' } : null
    }

    return {
      kind: 'options',
      options: round.options,
      selectedId:
        draft.selected !== null && draft.selected.roundIndex === index
          ? draft.selected.option.id
          : null,
      // 流没结束时后面可能还有新选项，现在选中的编号随时会被打乱
      disabled: !isActive || streaming,
      hint: !isActive
        ? '这是之前某一轮的选项，仅供回看'
        : streaming
          ? `AI 正在继续给出选项，已收到 ${round.options.length} 条…`
          : undefined,
      onSelect: (option) =>
        setDraft((prev) => ({ ...prev, selected: { roundIndex: index, option } })),
    }
  }

  return (
    <Card
      className="page-transition"
      title="智能提单"
      style={{
        // 占满内容区（顶栏 64 + 上下外边距各 16），对话区才能撑开、输入框才能贴底。
        // 用 dvh 而不是 vh：手机浏览器地址栏收起时 vh 不会跟着变，输入框会被顶出屏幕
        height: 'calc(100dvh - 96px)',
        minHeight: 460,
        display: 'flex',
        flexDirection: 'column',
      }}
      styles={{
        body: { flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 12 },
      }}
      extra={
        draft.rounds.length > 0 ? (
          <Button icon={<ReloadOutlined />} onClick={onReset}>
            重新开始
          </Button>
        ) : null
      }
    >
      {/* 只有这一块滚动；输入框是它的兄弟节点，永远贴在底部 */}
      <div
        ref={scrollRef}
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          paddingInline: 4,
        }}
      >
        {draft.rounds.length === 0 && (
          <div
            style={{
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: token.borderRadiusLG,
              background: token.colorFillQuaternary,
              padding: token.paddingLG,
            }}
          >
            <Flex gap={12} align="flex-start">
              <Avatar
                size={40}
                icon={<RobotOutlined />}
                style={{
                  flexShrink: 0,
                  background: token.colorPrimary,
                  color: token.colorTextLightSolid,
                }}
              />
              <div style={{ minWidth: 0 }}>
                <Text strong style={{ fontSize: token.fontSizeLG }}>
                  你好，我是智能提单助手
                </Text>
                <Paragraph style={{ margin: '6px 0 0' }}>
                  把遇到的问题告诉我就行：我来判断它属于哪一类工单 —— 信息够了，我会列出几种可能让你挑；
                  还缺关键信息，我会先反问你一句。你确认之后，我再帮你把工单填好。
                </Paragraph>
              </div>
            </Flex>

            <div style={{ marginTop: token.marginMD }}>
              <Text strong>描述时带上这三点，我猜得更准</Text>
              <Flex vertical gap={6} style={{ marginTop: 8 }}>
                {GUIDE_TIPS.map((tip, index) => (
                  <Flex key={tip.label} gap={8} align="flex-start">
                    <span
                      style={{
                        flexShrink: 0,
                        width: 18,
                        height: 18,
                        borderRadius: '50%',
                        background: token.colorPrimaryBg,
                        color: token.colorPrimary,
                        fontSize: token.fontSizeSM,
                        lineHeight: '18px',
                        textAlign: 'center',
                      }}
                    >
                      {index + 1}
                    </span>
                    <div style={{ fontSize: token.fontSize, lineHeight: token.lineHeight }}>
                      <Text strong>{tip.label}</Text>
                      <Text type="secondary">：{tip.hint}</Text>
                    </div>
                  </Flex>
                ))}
              </Flex>
              <Text
                type="secondary"
                style={{ display: 'block', marginTop: token.marginXS, fontSize: token.fontSizeSM }}
              >
                给出的选项都不像？接着补充说明就好，我会重新给一组。
              </Text>
            </div>
          </div>
        )}

        {draft.rounds.map((round, index) => {
          const content = aiBubble(round, index)
          return (
            <Fragment key={index}>
              <ChatBubble role="user" content={{ kind: 'text', text: round.input }} />
              {content !== null && (
                <ChatBubble role="ai" content={content} tag={round.model} />
              )}
            </Fragment>
          )
        })}

        {noOption && (
          <ChatBubble
            role="ai"
            content={{
              kind: 'notice',
              level: 'warning',
              title: '没有解析出任何选项',
              description: '模型这次的输出不符合约定的格式。补充说明后重试通常就好了。',
            }}
          />
        )}

        {error !== null && (
          <ChatBubble
            role="ai"
            content={{ kind: 'notice', level: 'error', title: '分析失败', description: error }}
          />
        )}
      </div>

      {selectedHere !== null && (
        <Auth
          code={AI_PERMISSIONS.assistSubmit}
          fallback={<Text type="secondary">没有「AI 辅助填单」权限，请联系管理员</Text>}
        >
          <Button
            block
            size="large"
            type="primary"
            icon={<FileAddOutlined />}
            disabled={streaming}
            onClick={onCreateWorkOrder}
          >
            用选中的选项生成工单（#{selectedHere.id} {selectedHere.title}）
          </Button>
        </Auth>
      )}

      <Flex gap={8} align="flex-end">
        {/* 边框直接画在 textarea 上：可见区域就是可点区域，点框内任何地方都能落焦点。
            字数绝对定位压在右下角（并让鼠标事件穿透），不占文字位置。
            上下 8 + 一行文字 22 + 边框 2 = 40，正好等于右侧 size="large" 的按钮。 */}
        <div style={{ position: 'relative', flex: 1, minWidth: 0, display: 'flex' }}>
          <TextArea
            // 右边留出 78px 给字数（最长的 "2000/2000" 约 60px + 12px 间距），文字不会跑到它底下
            style={{ padding: '8px 78px 8px 12px' }}
            value={composerValue}
            onChange={(event) => setComposerValue(event.target.value)}
            placeholder={
              isFirst
                ? '例如：财务系统导出报表时一直转圈'
                : asking
                  ? '回答它上面的问题，比如：昨天上午，行政楼'
                  : '补充一些细节，比如：昨天升级之后才开始的'
            }
            // 单行起步，写长了自动长高（最多 6 行）
            autoSize={{ minRows: 1, maxRows: 6 }}
            maxLength={2000}
            disabled={streaming}
            // 回车即发送，换行用 Shift+Enter —— 聊天框的默认手势。
            // 输入法正在组词时的回车是"选词"，不能当发送（否则中文用户每打一个词就发一次）
            onPressEnter={(event) => {
              if (event.shiftKey || event.nativeEvent.isComposing) {
                return
              }
              event.preventDefault()
              onSend()
            }}
          />
          <Text
            type="secondary"
            style={{
              position: 'absolute',
              right: 12,
              bottom: 8,
              fontSize: token.fontSizeSM,
              lineHeight: '22px',
              whiteSpace: 'nowrap',
              pointerEvents: 'none',
            }}
          >
            {composerValue.length}/2000
          </Text>
        </div>

        <Space size={8} style={{ flexShrink: 0 }}>
          {streaming && (
            <Button danger size="large" onClick={onInterrupt}>
              中断
            </Button>
          )}
          <Button
            type="primary"
            size="large"
            icon={<SendOutlined />}
            loading={streaming}
            disabled={composerValue.trim() === ''}
            onClick={onSend}
          >
            {isFirst ? '开始分析' : asking ? '提交回答' : '继续分析'}
          </Button>
        </Space>
      </Flex>
    </Card>
  )
}
