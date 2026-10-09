import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Collapse,
  Divider,
  Empty,
  Space,
  Spin,
  Typography,
  Input,
} from 'antd'
import { ReloadOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'

import { AI_PERMISSIONS, startIntentInference } from '../../api/ai'
import type { IntentOption } from '../../api/ai'
import { clearDraft, emptyDraft, loadDraft, saveDraft } from '../../api/aiSession'
import type { IntentDraft } from '../../api/aiSession'
import { Auth } from '../../permission/Auth'
import { IntentOptionCard } from './components/IntentOptionCard'
import { RawOutputPanel } from './components/RawOutputPanel'

const { Text, Paragraph } = Typography
const { TextArea } = Input

/**
 * 智能提单第一步：意图推断。
 *
 * ## 交互形状
 * 用户先说一段问题描述 → AI 流式吐出一串候选意图，**每解析出一条就渲染一张卡片**
 * （不是等它全部说完再一起显示）→ 用户选中最像的那张，或者继续补充说明。
 *
 * ## 补充之后为什么是"替换"而不是"追加"
 * 每一轮补充都是一次**新的推断**，旧的那组选项是针对旧描述的，放在一起只会让人
 * 分不清哪张是哪一轮的。所以新一轮开始时当前卡片区立刻清空（后端每轮也会重新给一组
 * 新的 id）。但历史并没有丢：每一轮的输入和它的选项都收在上面的「历史轮次」里可回看 ——
 * 用了两轮才说清问题的人，回头还能确认自己第一轮说了什么。
 *
 * ## 状态的持久化
 * 草稿（每轮的输入 + 选项 + 选中的那张）落在 sessionStorage 里，见 `api/aiSession.ts`。
 * 它同时是下一步的输入：填单页的「原始描述」**以这里记录的用户原话为准**，
 * 而不是信模型转述的版本。
 */
export default function IntentInference() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  // sessionStorage 是同步的，所以直接当 useState 的初始值 ——
  // 不需要"挂载后再 setState"的 effect（那个模式还会多渲染一帧空页面）
  const [draft, setDraft] = useState<IntentDraft>(() => loadDraft() ?? emptyDraft())
  const [input, setInput] = useState('')
  const [supplement, setSupplement] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [noOption, setNoOption] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [rawLines, setRawLines] = useState<string[]>([])
  const abortRef = useRef<AbortController | null>(null)

  // 每次变化都落盘：中途刷新页面时，前面几轮和会话 id 都还在
  useEffect(() => {
    saveDraft(draft)
  }, [draft])

  // 离开页面时掐掉流。不掐的话它会一直读到模型把话说完，
  // 期间还在往一个已经卸载的组件写状态（React 不会报错，但请求白白挂着）
  useEffect(() => {
    return () => abortRef.current?.abort()
  }, [])

  const run = useCallback(
    async (text: string) => {
      const controller = new AbortController()
      abortRef.current = controller
      const conversationId = draft.conversationId

      setStreaming(true)
      setError(null)
      setNoOption(false)
      setRawLines([])
      // 先放一个空的占位轮次：选项会一条条填进来，而上一轮的卡片就此从"当前"变成"历史"
      setDraft((prev) => ({ ...prev, rounds: [...prev.rounds, { input: text, options: [] }] }))

      let received = 0
      try {
        await startIntentInference({
          conversationId,
          userInput: text,
          signal: controller.signal,
          // 首轮的会话 id 是后端生成的，就在这个事件里
          onStart: (id) => setDraft((prev) => ({ ...prev, conversationId: id })),
          onRawLine: (line) => setRawLines((prev) => [...prev, line]),
          onOption: (option) => {
            received += 1
            // 永远写到**最后一个**轮次上：用闭包里算好的下标会读到过期的 draft，
            // "最后一个"则天然是对的
            setDraft((prev) => {
              const last = prev.rounds.length - 1
              if (last < 0) {
                return prev
              }
              const rounds = [...prev.rounds]
              rounds[last] = { ...rounds[last], options: [...rounds[last].options, option] }
              return { ...prev, rounds }
            })
          },
          onError: (msg) => setError(msg),
        })
      } finally {
        setStreaming(false)
        abortRef.current = null
        // 中途自己中断的不算"没解析出选项"，别把它报成解析失败
        setNoOption(!controller.signal.aborted && received === 0)
      }
    },
    [draft.conversationId],
  )

  const onAnalyse = () => {
    const text = input.trim()
    if (text === '' || streaming) {
      return
    }
    setInput('')
    void run(text)
  }

  const onSupplement = () => {
    const text = supplement.trim()
    if (text === '' || streaming) {
      return
    }
    setSupplement('')
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
    setRawLines([])
    setError(null)
    setNoOption(false)
  }

  const onCreateWorkOrder = () => {
    const selected = draft.selected
    if (!selected || draft.conversationId === null) {
      return
    }
    // 会话 id 与分析结果都留在草稿里，下一步据此预填表单
    navigate(
      `/ops/ai/submit?conversationId=${encodeURIComponent(draft.conversationId)}&option=${selected.option.id}`,
    )
  }

  const activeIndex = draft.rounds.length - 1
  const activeRound = activeIndex >= 0 ? draft.rounds[activeIndex] : null
  const historyRounds = draft.rounds.slice(0, Math.max(activeIndex, 0))
  const selected = draft.selected
  const selectedHere =
    selected !== null && selected.roundIndex === activeIndex ? selected.option.id : null

  return (
    <Card
      className="page-transition"
      title="智能提单"
      extra={
        draft.rounds.length > 0 ? (
          <Button icon={<ReloadOutlined />} onClick={onReset}>
            重新开始
          </Button>
        ) : null
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="先描述你遇到的问题，AI 会列出几种可能的意图让你确认"
          description={
            <Paragraph style={{ margin: 0 }}>
              每一条选项右侧的概率是 AI 的把握程度。如果都不像，就在下面继续补充说明 ——
              <Text strong> 追加说明后 AI 会重新给一组选项，当前这一组会被替换掉</Text>
              （历史轮次可以在上方展开回看）。选定一条后即可进入填单。
            </Paragraph>
          }
        />

        {historyRounds.length > 0 && (
          <Collapse
            items={historyRounds.map((round, index) => ({
              key: String(index),
              label: `历史轮次 ${index + 1}：${round.input}`,
              children: (
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Text type="secondary">这一轮 AI 给出的选项：</Text>
                  {round.options.length === 0 ? (
                    <Text type="secondary">（无）</Text>
                  ) : (
                    round.options.map((option) => (
                      <Text key={option.id}>
                        #{option.id} {option.title}（{option.probability}%）
                      </Text>
                    ))
                  )}
                </Space>
              ),
            }))}
          />
        )}

        {activeRound === null ? (
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            <TextArea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="例如：财务系统导出报表时一直转圈，十分钟也不出来"
              autoSize={{ minRows: 3, maxRows: 8 }}
              maxLength={2000}
              showCount
            />
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={streaming}
              disabled={input.trim() === ''}
              onClick={onAnalyse}
            >
              开始分析
            </Button>
          </Space>
        ) : (
          <>
            {/* antd v6 把标题位置从 orientation 拆成了 titlePlacement（orientation 现在只表示横/竖） */}
            <Divider titlePlacement="start" plain>
              本轮选项（{activeRound.options.length}）
            </Divider>

            {activeRound.options.length === 0 ? (
              <Space direction="vertical" align="center" style={{ width: '100%', padding: '24px 0' }}>
                {streaming ? (
                  <>
                    <Spin />
                    <Text type="secondary">正在分析…</Text>
                  </>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="这一轮没有给出选项，可以在下面补充说明后重试"
                  />
                )}
              </Space>
            ) : (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {activeRound.options.map((option) => (
                  <IntentOptionCard
                    key={option.id}
                    option={option}
                    selected={selectedHere === option.id}
                    disabled={streaming}
                    onSelect={(picked: IntentOption) =>
                      setDraft((prev) => ({
                        ...prev,
                        // 选中的是"当前最后一轮"里的某一条，下标在这里现算
                        selected: { roundIndex: prev.rounds.length - 1, option: picked },
                      }))
                    }
                  />
                ))}
              </Space>
            )}

            {/* 已经出卡片之后还得让人知道"没完"：选项是逐条回来的，先到的那张很容易
                被当成全部，用户就拿着它直接去建单了 */}
            {streaming && activeRound.options.length > 0 && (
              <Space size={6}>
                <Spin size="small" />
                <Text type="secondary">
                  AI 正在继续给出选项，已收到 {activeRound.options.length} 条…
                </Text>
              </Space>
            )}

            {streaming && (
              <Button danger onClick={onInterrupt}>
                中断本轮分析
              </Button>
            )}

            {/* antd v6 把标题位置从 orientation 拆成了 titlePlacement（orientation 现在只表示横/竖） */}
            <Divider titlePlacement="start" plain>
              补充说明
            </Divider>
            <TextArea
              value={supplement}
              onChange={(event) => setSupplement(event.target.value)}
              placeholder="补充一些细节，比如：是昨天升级之后才开始的；只影响导出，查询正常"
              autoSize={{ minRows: 2, maxRows: 6 }}
              maxLength={2000}
              showCount
              disabled={streaming}
            />
            <Space wrap>
              <Button
                icon={<SendOutlined />}
                loading={streaming}
                disabled={supplement.trim() === ''}
                onClick={onSupplement}
              >
                继续分析
              </Button>
              <Auth
                code={AI_PERMISSIONS.assistSubmit}
                fallback={<Text type="secondary">没有「AI 辅助填单」权限，请联系管理员</Text>}
              >
                <Button
                  type="primary"
                  disabled={selected === null || streaming}
                  onClick={onCreateWorkOrder}
                >
                  用选中的选项生成工单
                </Button>
              </Auth>
              {selected !== null && selected.roundIndex === activeIndex && (
                <Text type="secondary">
                  已选中 #{selected.option.id} {selected.option.title}
                </Text>
              )}
            </Space>
          </>
        )}

        {error !== null && <Alert type="error" showIcon message="分析失败" description={error} />}

        {noOption && (
          <Alert
            type="warning"
            showIcon
            message="没有解析出任何选项"
            description="模型这次的输出不符合约定的 JSONL 格式。展开下面的「原始输出」可以看到它到底返回了什么；补充说明后重试通常就好了。"
          />
        )}

        <RawOutputPanel lines={rawLines} />
      </Space>
    </Card>
  )
}
