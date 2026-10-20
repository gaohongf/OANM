import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import {
  App as AntdApp,
  Avatar,
  Button,
  Card,
  Flex,
  Image,
  Input,
  Space,
  Spin,
  Tooltip,
  Typography,
  theme,
} from 'antd'
import {
  CloseOutlined,
  FileAddOutlined,
  PictureOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router'

import { generateWorkOrderDraft, startIntentInference, submitWorkOrderDraft } from '../../api/ai'
import type { IntentNode, WorkOrderDraft } from '../../api/ai'
import { clearDraft, draftInputRounds, emptyDraft, loadDraft, saveDraft } from '../../api/aiSession'
import type { IntentDraft, IntentRound } from '../../api/aiSession'
import { uploadFile } from '../../api/files'
import type { UploadedFile } from '../../api/files'
import { ChatBubble } from './components/ChatBubble'
import type { BubbleContent } from './components/ChatBubble'
import { WorkOrderDraftDrawer } from './components/WorkOrderDraftDrawer'
import type { WorkOrderFormValues } from './components/WorkOrderDraftDrawer'

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

/** 每条消息最多几张图。后端有同样的校验，这里先挡是为了给出即时反馈。 */
const MAX_IMAGES = 3
/** 单张图片上限。service-files 的 multipart 上限是 50MB，这条是产品约束。 */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const MAX_IMAGE_SIZE_TEXT = '10MB'

/**
 * 已经选好、正在上传（或上传完）但还没随消息发出的图片。
 * 它只活在当前页面：刷新后未发送的图片会丢，已进入对话的那些随轮次持久化。
 */
interface PendingAttachment {
  uid: string
  name: string
  status: 'uploading' | 'ready' | 'failed'
  /** 上传成功后的结果，`status === 'ready'` 时才有 */
  file?: UploadedFile
  error?: string
}

/**
 * 智能提单：意图推断 + 工单生成，都在这一页完成。
 *
 * ## 交互形状
 * 一整段对话：用户说的话靠右，AI 的作答靠左。AI 这一轮可能给一组候选意图卡片，
 * 也可能（缺时间、地点这类关键信息时）反过来问一句 —— 两种形态都是"这一轮它说了什么"，
 * 所以都落在同一条气泡里，只是气泡类型不同（见 `ChatBubble` 的 `BubbleContent`）。
 *
 * 用户选中一条选项、点「生成工单」之后，会话末尾会多出一条工单气泡：
 * 先显示骨架，生成完成后变成可点开的卡片。点右侧「查看并提交」在抽屉里确认/修改，
 * 提交由 service-ai 带着当前登录态转发给工单服务。
 *
 * 后端是一个工作流（判定 → 追问 / 选项 → 生成），每条 `chat` 帧带着产出它的节点名
 * （见 `api/ai.ts` 的 `IntentNode`）。生成也是同一段会话的下一轮，只是这次带的是
 * 选中的选项编号。
 *
 * ## 为什么只保留一个输入框
 * "第一句描述"和"后续补充"对用户是同一件事，没必要换一个框、换一套按钮文案。
 * 只有 placeholder 和按钮文字会随上下文变（开始分析 / 继续分析 / 提交回答）。
 *
 * ## 为什么只有最新一轮的选项能点
 * 选项编号每轮都从 1 开始，而生成时只带编号。让用户选第二轮的 #2、却去生成第一轮的 #2，
 * 后端分不清指的是哪一条。历史轮次的卡片保留下来但只读，并标一句"仅供回看"。
 *
 * ## 状态的持久化
 * 草稿（每轮的输入 + 选项 + 选中的那张 + 最近生成的工单）落在 sessionStorage 里，
 * 见 `api/aiSession.ts`。
 *
 * ## 图片附件与识图
 * 选图后**立即上传**（service-files），待发送列表只活在本页；发送时把已上传的地址记进
 * 本轮草稿，并随 `userInput` 一起传给 service-ai。后端先识图、再据此做意图推断；
 * 识图结果只留在后端，用户看到的是结合图片内容的追问或选项。
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
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  /** 待发送图片：选图后立即上传，发送时只带走上传成功的那些 */
  const [pending, setPending] = useState<PendingAttachment[]>([])
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  /** 待发送图片的本地 key，只用于 React 列表和上传回调定位，不进草稿 */
  const pendingUid = useRef(0)
  const abortRef = useRef<AbortController | null>(null)
  /** 工单生成是独立的流：中断/重新开始时要能单独掐掉，不能和意图推断共用一个 */
  const generationAbortRef = useRef<AbortController | null>(null)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  /** 上一帧的轮次数，用来判断"是不是用户刚发了新消息"（见下面的滚动 effect） */
  const lastRoundCount = useRef(0)

  const generating = draft.workOrder?.status === 'generating'
  /**
   * 工单已经创建：这段会话到此结束。
   * 不能再生成第二张 —— 补充一句话会清掉上一张工单，按钮就又回来了。
   * 想再提一单，走「重新开始」开一段新会话。
   */
  const submitted = draft.workOrder?.status === 'submitted'
  /**
   * 护栏判定这段会话已结束（无关输入累计超限）。
   * 后端已经不再接受它的后续输入，界面上也只能「重新开始」。
   * 用 some 而不是只看最后一轮：刷新后重新点开这段草稿时判断结果应当一致。
   */
  const sessionEnded = draft.rounds.some((round) => round.ended)

  // 每次变化都落盘：中途刷新页面时，前面几轮和会话 id 都还在
  useEffect(() => {
    saveDraft(draft)
  }, [draft])

  // 离开页面时掐掉流。不掐的话它会一直读到模型把话说完，
  // 期间还在往一个已经卸载的组件写状态（React 不会报错，但请求白白挂着）
  useEffect(() => {
    return () => {
      abortRef.current?.abort()
      generationAbortRef.current?.abort()
    }
  }, [])

  /**
   * 跟着最新内容滚到底。
   *
   * 两种情况下必须滚：用户自己刚发了一条；以及流还在往最后那条里加字。
   * 但用户往上翻历史的时候不要把他拽回来 —— 所以只有"本来就贴着底部"才跟随。
   * 工单气泡出现/变化时也要跟随（它不在 `rounds` 里，得单独进依赖）。
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
  }, [draft.rounds, draft.workOrder, streaming, error, noOption])

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
    async (text: string, attachments: UploadedFile[]) => {
      const controller = new AbortController()
      abortRef.current = controller
      const conversationId = draft.conversationId

      setStreaming(true)
      setError(null)
      setNoOption(false)
      // 先放一个占位轮次：选项会一条条填进来，而上一轮的卡片就此只读。
      // mode 先记 PENDING —— 此刻还不知道这轮会拿回选项还是追问。
      // 会话已经往前走，上一张工单不再对应当前描述，一并清掉。
      setDraft((prev) => ({
        ...prev,
        selected: null,
        workOrder: null,
        rounds: [
          ...prev.rounds,
          {
            input: text,
            mode: 'PENDING',
            model: '',
            question: '',
            refusal: '',
            ended: false,
            options: [],
            attachments,
          },
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
          // 后端据此读取图片字节交给模型识图
          images: attachments.map((attachment) => attachment.url),
          signal: controller.signal,
          // 首轮的 threadId 是后端生成的，就在这个事件里 ——
          // 它同时是下一轮、以及生成/提交工单要带的会话标识
          onWorkflow: (threadId) => setDraft((prev) => ({ ...prev, conversationId: threadId })),
          // 模型名贴在气泡名字旁边；以后人工介入时这里记的是坐席
          onModel: (modelName) => patchActiveRound((round) => ({ ...round, model: modelName })),
          onMode: (node) => {
            mode = node
            patchActiveRound((round) => ({ ...round, mode: node }))
          },
          // 收到的每次都是完整一段（不是增量），直接覆盖
          onQuestion: (question) => patchActiveRound((round) => ({ ...round, question })),
          // 护栏拦下这一轮：AI 的话就是拒绝说明；ended 表示会话已被后端结束
          onGuardrail: (refusal, ended) =>
            patchActiveRound((round) => ({ ...round, refusal, ended })),
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
        // 追问和护栏拒绝这两轮本来就没有也不该有选项，一条都没解析出来是**正确**结果
        setNoOption(
          !controller.signal.aborted && received === 0 && mode !== 'ask' && mode !== 'refused',
        )
      }
    },
    [draft.conversationId, patchActiveRound],
  )

  /**
   * 生成工单。
   *
   * 骨架立即出现 —— 模型要几秒到十几秒，先让用户看到"这条请求已经发出去并且在干活"。
   * 生成期间不允许再点一次（`loading` + 禁用），避免两个流同时往同一个会话里写。
   */
  const onGenerate = () => {
    const selected = draft.selected
    if (selected === null || draft.conversationId === null) {
      return
    }
    const controller = new AbortController()
    generationAbortRef.current = controller
    const conversationId = draft.conversationId
    const { roundIndex, option } = selected

    setDraft((prev) => ({
      ...prev,
      workOrder: {
        status: 'generating',
        draft: null,
        roundIndex,
        optionId: option.id,
        model: '',
        workOrderId: null,
        error: null,
      },
    }))

    void generateWorkOrderDraft({
      conversationId,
      userSelect: option.id,
      signal: controller.signal,
      onModel: (model) =>
        setDraft((prev) =>
          prev.workOrder === null
            ? prev
            : { ...prev, workOrder: { ...prev.workOrder, model } },
        ),
      onDraft: (workOrderDraft) =>
        setDraft((prev) =>
          prev.workOrder === null
            ? prev
            : {
                ...prev,
                workOrder: {
                  ...prev.workOrder,
                  status: 'ready',
                  draft: workOrderDraft,
                  error: null,
                },
              },
        ),
      onError: (msg) =>
        setDraft((prev) =>
          prev.workOrder === null
            ? prev
            : {
                ...prev,
                workOrder: { ...prev.workOrder, status: 'failed', error: msg },
              },
        ),
    }).finally(() => {
      // 只有当前这条流有资格收尾：重新开始/再次生成时旧流的 finally 是异步落地的
      if (generationAbortRef.current !== controller) {
        return
      }
      generationAbortRef.current = null
      // 流结束了但还是 generating（被中断、或一帧 draft 都没有）：不能把骨架留在屏幕上转
      setDraft((prev) => {
        if (prev.workOrder === null || prev.workOrder.status !== 'generating') {
          return prev
        }
        return {
          ...prev,
          workOrder: {
            ...prev.workOrder,
            status: 'failed',
            error: controller.signal.aborted
              ? '已取消生成'
              : '模型没有返回工单内容，请重新生成',
          },
        }
      })
    })
  }

  /** 第一句描述和后续补充共用同一个输入框，只是数据源和按钮文案不同 */
  const isFirst = draft.rounds.length === 0
  const composerValue = isFirst ? input : supplement
  const setComposerValue = isFirst ? setInput : setSupplement
  const busy = streaming || generating
  /** 还有图片在上传、或某张失败了没处理：这时不能发，否则消息会少图却没人察觉 */
  const attachmentBlocking = pending.some((item) => item.status !== 'ready')
  const canSend =
    composerValue.trim() !== '' &&
    !busy &&
    !submitted &&
    !sessionEnded &&
    !attachmentBlocking

  const removePending = (uid: string) => {
    // 只从待发送列表里去掉，不调删除接口：普通用户没有删除权限，
    // 未发送就放弃的文件会留在存储里（本轮接受的孤儿文件）
    setPending((prev) => prev.filter((item) => item.uid !== uid))
  }

  /**
   * 上传一张图，成功/失败都只改它自己那一项，不阻塞同一批里的其它图。
   * uid 在上传开始时已经定下，用户中途移除这一项时回调会自然落空（map 找不到）。
   */
  const uploadOne = async (uid: string, file: File, conversationId: string | null) => {
    try {
      const uploaded = await uploadFile(file, {
        objectType: 'AI_INTENT',
        objectId: conversationId ?? undefined,
      })
      setPending((prev) =>
        prev.map((item) =>
          item.uid === uid ? { ...item, status: 'ready', file: uploaded } : item,
        ),
      )
    } catch (err) {
      setPending((prev) =>
        prev.map((item) =>
          item.uid === uid
            ? {
                ...item,
                status: 'failed',
                error: err instanceof Error ? err.message : '上传失败',
              }
            : item,
        ),
      )
    }
  }

  /**
   * 选图。用原生 input 而不是 antd Upload：一次选择会同时给出整批文件，
   * 张数上限可以直接按"当前已有 + 本批"判断；Upload 的 beforeUpload 是逐个调用的，
   * 那样得在回调之外再维护一份计数才能防止超限。
   */
  const onPickImages = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    // 清空 value：否则连续选同一个文件不会触发 change
    event.target.value = ''
    if (files.length === 0) {
      return
    }

    const remaining = MAX_IMAGES - pending.length
    if (remaining <= 0) {
      message.warning(`每条消息最多 ${MAX_IMAGES} 张图片`)
      return
    }
    if (files.length > remaining) {
      message.warning(`每条消息最多 ${MAX_IMAGES} 张图片，本次只保留前 ${remaining} 张`)
    }

    for (const file of files.slice(0, remaining)) {
      if (!file.type.startsWith('image/')) {
        message.warning(`「${file.name}」不是图片`)
        continue
      }
      if (file.size > MAX_IMAGE_SIZE) {
        message.warning(`「${file.name}」超过 ${MAX_IMAGE_SIZE_TEXT}`)
        continue
      }
      pendingUid.current += 1
      const uid = String(pendingUid.current)
      setPending((prev) => [...prev, { uid, name: file.name, status: 'uploading' }])
      void uploadOne(uid, file, draft.conversationId)
    }
  }

  const onSend = () => {
    const text = composerValue.trim()
    if (!canSend) {
      return
    }
    const attachments = pending.flatMap((item) =>
      item.status === 'ready' && item.file ? [item.file] : [],
    )
    if (isFirst) {
      setInput('')
    } else {
      setSupplement('')
    }
    setPending([])
    void run(text, attachments)
  }

  const onInterrupt = () => {
    abortRef.current?.abort()
    generationAbortRef.current?.abort()
    message.info('已中断当前操作')
  }

  const onReset = () => {
    abortRef.current?.abort()
    generationAbortRef.current?.abort()
    setDrawerOpen(false)
    clearDraft()
    setDraft(emptyDraft())
    setError(null)
    setNoOption(false)
    // 未发送的图片跟着这段会话一起作废；已上传的文件不删（没有删除权限）
    setPending([])
  }

  const activeIndex = draft.rounds.length - 1
  const activeRound = activeIndex >= 0 ? draft.rounds[activeIndex] : null
  /** 当前这一轮 AI 在**追问**，而不是给选项。输入框的语义与文案都要跟着走 */
  const asking = activeRound?.mode === 'ask'
  const selectedHere =
    draft.selected !== null && draft.selected.roundIndex === activeIndex
      ? draft.selected.option
      : null

  /** 抽屉里的改动回写草稿（落 sessionStorage），关掉再打开时还在 */
  const onDraftChange = (workOrderDraft: WorkOrderDraft) => {
    setDraft((prev) =>
      prev.workOrder === null
        ? prev
        : { ...prev, workOrder: { ...prev.workOrder, draft: workOrderDraft } },
    )
  }

  const onSubmitWorkOrder = async (values: WorkOrderFormValues) => {
    const generation = draft.workOrder
    if (generation === null || draft.conversationId === null) {
      return
    }
    setSubmitting(true)
    try {
      const created = await submitWorkOrderDraft({
        conversationId: draft.conversationId,
        // 用生成时记下的选项编号，而不是"当前选中"：用户可能在生成后又点了别的卡片
        userSelect: generation.optionId,
        title: values.title,
        type: values.type,
        priority: values.priority,
        problemDescription: values.problemDescription,
        originalProblemDescription: draftInputRounds(draft).join('\n'),
        solutionDetail: values.solutionDetail,
      })
      setDraft((prev) => {
        if (prev.workOrder === null) {
          return prev
        }
        const edited =
          prev.workOrder.draft === null
            ? null
            : {
                ...prev.workOrder.draft,
                title: values.title,
                type: values.type,
                priority: values.priority,
                problemDescription: values.problemDescription,
                solutionDetail: values.solutionDetail ?? '',
              }
        return {
          ...prev,
          workOrder: {
            ...prev.workOrder,
            status: 'submitted',
            draft: edited,
            workOrderId: created.id,
            error: null,
          },
        }
      })
      setDrawerOpen(false)
      message.success('工单已提交')
    } catch (err) {
      // 抽屉不关：用户能在同一张表上改了再交
      message.error(err instanceof Error ? err.message : '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const onViewWorkOrder = () => {
    const id = draft.workOrder?.workOrderId
    if (id) {
      navigate(`/ops/work-order?openId=${encodeURIComponent(id)}`)
    }
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

    if (round.mode === 'refused') {
      if (round.refusal !== '') {
        return { kind: 'text', text: round.refusal }
      }
      // 护栏节点定了但第一个字还没到
      return streaming && isActive
        ? { kind: 'pending', text: '正在检查输入…' }
        : null
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
      // 流没结束时后面可能还有新选项，现在选中的编号随时会被打乱；
      // 工单已创建后整段会话只读
      disabled: !isActive || streaming || generating || submitted,
      hint: submitted
        ? '工单已创建，这段会话已结束'
        : !isActive
          ? '这是之前某一轮的选项，仅供回看'
          : streaming
            ? `AI 正在继续给出选项，已收到 ${round.options.length} 条…`
            : generating
              ? '正在按选中的选项生成工单…'
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
                  还缺关键信息，我会先反问你一句。你确认之后，我再帮你把工单填好，你可以点开工单修改并提交。
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
              <ChatBubble
                role="user"
                content={{ kind: 'text', text: round.input, attachments: round.attachments }}
              />
              {content !== null && (
                <ChatBubble role="ai" content={content} tag={round.model} />
              )}
            </Fragment>
          )
        })}

        {/* 工单不是某一轮的作答，而是整段对话的产物 —— 所以在所有轮次之后单列一条 */}
        {draft.workOrder !== null && (
          <ChatBubble
            role="ai"
            tag={draft.workOrder.model}
            content={{
              kind: 'workorder',
              status: draft.workOrder.status,
              draft: draft.workOrder.draft,
              workOrderId: draft.workOrder.workOrderId,
              error: draft.workOrder.error,
              onOpen: () => setDrawerOpen(true),
              onView: onViewWorkOrder,
            }}
          />
        )}

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

      {/* 工单创建后不再提供生成入口：同一段会话只出一张单 */}
      {selectedHere !== null && !submitted && (
        <Button
          block
          size="large"
          type="primary"
          icon={<FileAddOutlined />}
          loading={generating}
          disabled={busy}
          onClick={onGenerate}
        >
          生成工单（#{selectedHere.id} {selectedHere.title}）
        </Button>
      )}

      {/* 待发送的图片：上传成功的显示缩略图，上传中/失败显示占位与原因。
          放在输入框上方而不是塞进气泡里 —— 它们还没成为"说过的话"。 */}
      {pending.length > 0 && (
        <Flex gap={8} wrap>
          {pending.map((item) => (
            <div key={item.uid} style={{ position: 'relative', width: 72, height: 72 }}>
              {item.status === 'ready' && item.file ? (
                <Image
                  src={item.file.url}
                  alt={item.name}
                  width={72}
                  height={72}
                  style={{
                    objectFit: 'cover',
                    borderRadius: token.borderRadius,
                    border: `1px solid ${token.colorBorderSecondary}`,
                  }}
                />
              ) : (
                <Tooltip
                  title={item.status === 'failed' ? (item.error ?? '上传失败') : '正在上传…'}
                >
                  <div
                    style={{
                      width: 72,
                      height: 72,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 4,
                      padding: 4,
                      borderRadius: token.borderRadius,
                      border: `1px dashed ${
                        item.status === 'failed' ? token.colorErrorBorder : token.colorBorderSecondary
                      }`,
                      background: item.status === 'failed' ? token.colorErrorBg : token.colorFillQuaternary,
                    }}
                  >
                    {item.status === 'uploading' ? (
                      <Spin size="small" />
                    ) : (
                      <Text type="danger" style={{ fontSize: token.fontSizeSM }}>
                        上传失败
                      </Text>
                    )}
                    <Text
                      type="secondary"
                      ellipsis
                      style={{ fontSize: token.fontSizeSM, maxWidth: '100%' }}
                    >
                      {item.name}
                    </Text>
                  </div>
                </Tooltip>
              )}
              <Button
                size="small"
                shape="circle"
                icon={<CloseOutlined />}
                onClick={() => removePending(item.uid)}
                aria-label={`移除 ${item.name}`}
                style={{ position: 'absolute', top: -8, right: -8 }}
              />
            </div>
          ))}
        </Flex>
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
              sessionEnded
                ? '本次会话已结束，请点右上角「重新开始」'
                : submitted
                  ? '工单已创建，如需再次提单请点右上角「重新开始」'
                  : isFirst
                    ? '例如：财务系统导出报表时一直转圈'
                    : asking
                      ? '回答它上面的问题，比如：昨天上午，行政楼'
                      : '补充一些细节，比如：昨天升级之后才开始的'
            }
            // 单行起步，写长了自动长高（最多 6 行）
            autoSize={{ minRows: 1, maxRows: 6 }}
            maxLength={2000}
            disabled={busy || submitted || sessionEnded}
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
          {busy && (
            <Button danger size="large" onClick={onInterrupt}>
              中断
            </Button>
          )}
          <Tooltip
            title={pending.length >= MAX_IMAGES ? `每条消息最多 ${MAX_IMAGES} 张图片` : '添加图片'}
          >
            <Button
              size="large"
              icon={<PictureOutlined />}
              disabled={busy || submitted || sessionEnded || pending.length >= MAX_IMAGES}
              onClick={() => fileInputRef.current?.click()}
              aria-label="添加图片"
            />
          </Tooltip>
          <Button
            type="primary"
            size="large"
            icon={<SendOutlined />}
            loading={streaming}
            disabled={!canSend}
            onClick={onSend}
          >
            {isFirst ? '开始分析' : asking ? '提交回答' : '继续分析'}
          </Button>
        </Space>
      </Flex>

      {/* 原生 file input：一次选择给出整批文件，便于统一校验张数 */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        multiple
        hidden
        onChange={onPickImages}
      />

      <WorkOrderDraftDrawer
        open={drawerOpen}
        draft={draft.workOrder?.draft ?? null}
        originalDescription={draftInputRounds(draft).join('\n')}
        submitting={submitting}
        onClose={() => setDrawerOpen(false)}
        onChange={onDraftChange}
        onSubmit={(values) => void onSubmitWorkOrder(values)}
      />
    </Card>
  )
}
