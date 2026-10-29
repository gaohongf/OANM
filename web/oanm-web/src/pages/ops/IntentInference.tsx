import {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react'
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
  SendOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router'

import {
  continueDecision,
  decisionActionOf,
  generateWorkOrderDraft,
  startIntentInference,
  submitWorkOrderDraft,
} from '../../api/ai'
import type {
  DecisionAction,
  IntentNode,
  IntentOption,
  KnowledgeSource,
  WorkOrderDraft,
} from '../../api/ai'
import { clearDraft, draftInputRounds, emptyDraft, loadDraft, saveDraft } from '../../api/aiSession'
import type { IntentDraft, IntentRound } from '../../api/aiSession'
import { IMAGE_MAX_BYTES, uploadFile } from '../../api/files'
import type { UploadedFile } from '../../api/files'
import { useAuthStore } from '../../store/auth'
import type { MenuNode } from '../../types/api'
import { AI_AVATAR, ChatBubble } from './components/ChatBubble'
import type { BubbleContent, SolutionSteps } from './components/ChatBubble'
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
  { label: '地点', hint: '您在哪里、哪间办公室或者哪个厂房，比如「行政楼三楼」「财务办公室」' },
  { label: '现象', hint: '你看到了什么、做到哪一步出的错，比如「导出报表一直转圈」' },
]

/**
 * 卡片下方的操作提示。
 *
 * 不给提示时，用户不知道这些卡片是可以点的、更不知道点完会发生什么 ——
 * 这两句就是为此存在的，措辞尽量口语化。
 */
const OPTION_HINT = '请点击最接近的一项；如果都不像，直接在下面继续补充说明。'
const DECISION_HINT = '请点击上面的选项，也可以直接在下面回复「已经解决了」/「帮我提工单」。'
const PROBE_HINT = '点最接近的一项；都不是就在下面说说实际看到的现象。'

/** 自助步骤折叠后保留几步：手机上一屏够读完 */
const COLLAPSED_STEPS = 2

/** 窄屏断点，与布局层的 md（768）一致：小于它按手机处理 */
const NARROW_QUERY = '(max-width: 767px)'

/**
 * 订阅窄屏状态。
 *
 * 用 matchMedia 而不是 antd 的 Grid.useBreakpoint：这里只需要一个布尔量，
 * matchMedia 首帧就能给出正确值，不会先按窄屏渲染一帧再切回来。
 */
function subscribeNarrow(onChange: () => void) {
  const query = window.matchMedia(NARROW_QUERY)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

function getNarrow(): boolean {
  return window.matchMedia(NARROW_QUERY).matches
}

/**
 * 输入框的占位文案。
 *
 * 窄屏必须用短句：TextArea 开着 autoSize，而它在内容为空时按
 * `value || placeholder` 量高度（见 @rc-component/input 的 calculateNodeHeight），
 * 手机上一句长提示会把本该一行的输入框撑成好几行的大方块。
 */
function composerPlaceholder(state: {
  narrow: boolean
  sessionEnded: boolean
  selfServiceDone: boolean
  submitted: boolean
  isFirst: boolean
  deciding: boolean
  asking: boolean
}): string {
  if (state.sessionEnded) {
    return state.narrow ? '会话已结束' : '本次会话已结束，请点右上角「重新开始」'
  }
  if (state.selfServiceDone) {
    return state.narrow ? '记录已生成' : '自助解决记录已生成，可在上面的卡片里查看并提交'
  }
  if (state.submitted) {
    return state.narrow ? '工单已创建' : '工单已创建，如需再次提单请点右上角「重新开始」'
  }
  if (state.isFirst) {
    return state.narrow ? '描述遇到的问题' : '例如：财务系统导出报表时一直转圈'
  }
  if (state.deciding) {
    return state.narrow ? '回复是否已解决' : '也可以直接回复：已经解决了 / 帮我提工单'
  }
  if (state.asking) {
    return state.narrow ? '回答上面的问题' : '回答它上面的问题，比如：昨天上午，行政楼'
  }
  return state.narrow ? '补充细节…' : '补充一些细节，比如：昨天升级之后才开始的'
}

/** 知识库浏览页的路径。出处链接跳到它的 `?openId=` 入口 */
const KNOWLEDGE_PATH = '/knowledge'

/** 侧栏里有没有这个页面 —— 没有就不给链接，免得点过去是"页面不存在" */
function hasPagePath(nodes: MenuNode[], path: string): boolean {
  return nodes.some((node) => node.path === path || hasPagePath(node.children, path))
}

/**
 * 把 AI 的一次作答拆成多条气泡。
 *
 * 提示词里追问和自助步骤都是"一句一行"写出来的：拆开后一句一个气泡，
 * 长回复不会挤成一大块，读起来也更像对面在连续说话。
 * 选项气泡上方那段说明（自助步骤）同样拆开，卡片本身仍是独立的一条。
 */
function splitBubbles(content: BubbleContent): BubbleContent[] {
  if (content.kind === 'text') {
    const lines = splitLines(content.text)
    return lines.length > 0 ? lines.map((text): BubbleContent => ({ kind: 'text', text })) : [content]
  }
  if (content.kind === 'options' && content.steps !== undefined) {
    // 步骤排成一条编号列表，卡片另起一条 —— 不再一句一个气泡，手机上不会刷屏
    return [{ kind: 'steps', ...content.steps }, { ...content, steps: undefined }]
  }
  if (content.kind === 'options' && content.text !== undefined) {
    return [
      ...splitLines(content.text).map((text): BubbleContent => ({ kind: 'text', text })),
      { ...content, text: undefined },
    ]
  }
  return [content]
}

/** 按换行切开，去掉空行和每行首尾空白；流式输出里半行还没写完也照常返回。 */
function splitLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '')
}

/**
 * 把自助解决建议排成「提醒 / 步骤 / 出处」。
 *
 * 约定来自后端提示词：一句一行，风险提醒以「注意：」开头且不算步骤，
 * 出处是「参考…」那一行，其余每行都是一步。
 * 模型偶尔仍会给步骤带序号，这里先去掉，免得界面上出现"1 1."。
 */
function parseSolution(text: string): Omit<SolutionSteps, 'collapsedCount'> {
  let note: string | undefined
  let footnote: string | undefined
  const steps: string[] = []

  splitLines(text).forEach((line) => {
    if (line.startsWith('参考')) {
      footnote = footnote === undefined ? line : `${footnote} ${line}`
      return
    }
    if (note === undefined && (line.startsWith('注意') || line.startsWith('提示'))) {
      note = line
      return
    }
    steps.push(line.replace(/^\d+\s*[.、)）]\s*/, ''))
  })

  return { note, steps, footnote }
}

/** 每条消息最多几张图。后端有同样的校验，这里先挡是为了给出即时反馈。 */
const MAX_IMAGES = 3
/** 单张图片上限，与后端图片上传接口一致；这里先挡是为了给出即时反馈。 */
const MAX_IMAGE_SIZE = IMAGE_MAX_BYTES
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
  // 手机上的输入区要更紧凑：短占位文案、图标按钮，避免输入框被撑成大方块
  const narrow = useSyncExternalStore(subscribeNarrow, getNarrow, () => false)
  // 没有知识库菜单（或没有读权限）时，出处的标题照样显示，但点不进去
  const menus = useAuthStore((state) => state.menus)
  const canOpenKnowledge = useMemo(() => hasPagePath(menus, KNOWLEDGE_PATH), [menus])

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
  /**
   * 下一次渲染后强制滚到底，一次性标记。
   * 用户点「生成工单」是一个明确的动作，不管他此刻是不是翻在上面看选项，
   * 都应该把新出现的工单气泡带进视野 —— 否则要手动往下滚一节才开始生成。
   */
  const forceScrollRef = useRef(false)

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
  /**
   * 自助解决的记录已经生成：后端这段对话已经走到终点，再发消息接不上去了。
   * 用户还能打开卡片确认/修改并提交，或者点「重新开始」开一段新会话。
   */
  const selfServiceDone =
    draft.workOrder !== null &&
    draft.workOrder.selfResolved &&
    draft.workOrder.status !== 'failed'

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

    // 点「生成工单」后的这一帧无条件跟随：工单气泡出现在所有轮次之后，
    // 用户可能正停在选项上方，此时按"是否贴着底部"判断会把他留在原地。
    const force = forceScrollRef.current
    forceScrollRef.current = false

    if (!force && !grew && el.scrollHeight - el.scrollTop - el.clientHeight > 160) {
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
    async (text: string, attachments: UploadedFile[], action?: DecisionAction) => {
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
            greeting: '',
            question: '',
            lead: '',
            solution: '',
            sources: [],
            refusal: '',
            ended: false,
            options: [],
            attachments,
          },
        ],
      }))

      let received = 0
      /**
       * 本轮最后一个作答节点。**在闭包外面**记一份，因为下面 finally 里要拿它判断
       * "没解析出选项"到底是不是个错误 —— 那时 draft 已经不容易读了。
       * 自助解决一轮会先后经过 solution / decision，以最后一次为准。
       */
      let mode: IntentNode | null = null
      /** 本轮模型名：自助解决记录的气泡标签要用 */
      let modelName = ''

      /**
       * 自助解决记录不是某一轮的答复，而是一张可以直接提交的卡片 ——
       * 放在所有轮次之后，和普通工单草稿同一个位置。
       */
      const onSelfResolvedDraft = (workOrderDraft: WorkOrderDraft) => {
        forceScrollRef.current = true
        setDraft((prev) => ({
          ...prev,
          workOrder: {
            status: 'ready',
            draft: workOrderDraft,
            roundIndex: Math.max(prev.rounds.length - 1, 0),
            optionId: null,
            selfResolved: true,
            model: modelName,
            workOrderId: null,
            error: null,
          },
        }))
      }

      /**
       * 两条流共用的回调。
       *
       * 文字也要接在这边：动作之后的回答形态由后端决定，`WORK_ORDER` 卡片会先追问
       * 缺少的时间地点，只接选项的话这句话就没地方落，界面会显示成"没有给出内容"。
       */
      const commonHandlers = {
        // 模型名贴在气泡名字旁边；以后人工介入时这里记的是坐席
        onModel: (name: string) => {
          modelName = name
          patchActiveRound((round) => ({ ...round, model: name }))
        },
        onMode: (node: IntentNode) => {
          mode = node
          patchActiveRound((round) => ({ ...round, mode: node }))
        },
        // 第一轮的开场白：和作答分开存，渲染时排在作答前面
        onGreeting: (greeting: string) =>
          patchActiveRound((round) => ({ ...round, greeting })),
        // 收到的每次都是完整一段（不是增量），直接覆盖
        onQuestion: (question: string) =>
          patchActiveRound((round) => ({ ...round, question })),
        onLead: (lead: string) => patchActiveRound((round) => ({ ...round, lead })),
        // 出处一条一帧地到，每次都覆盖成当前的全集
        onSources: (sources: KnowledgeSource[]) =>
          patchActiveRound((round) => ({ ...round, sources })),
        onSolution: (solution: string) =>
          patchActiveRound((round) => ({ ...round, solution })),
        // 护栏拦下这一轮：AI 的话就是拒绝说明；ended 表示会话已被后端结束
        onGuardrail: (refusal: string, ended: boolean) =>
          patchActiveRound((round) => ({ ...round, refusal, ended })),
        onOption: (option: IntentOption) => {
          received += 1
          patchActiveRound((round) => ({ ...round, options: [...round.options, option] }))
        },
        onSelfResolvedDraft,
        onError: (msg: string) => setError(msg),
      }

      try {
        if (action !== undefined) {
          if (conversationId === null) {
            return
          }
          // 点决策卡片：带动作而不是文字，后端直接走对应分支，不再问模型
          await continueDecision({
            conversationId,
            action,
            signal: controller.signal,
            ...commonHandlers,
          })
        } else {
          await startIntentInference({
            conversationId,
            userInput: text,
            // 后端据此读取图片字节交给模型识图
            images: attachments.map((attachment) => attachment.url),
            signal: controller.signal,
            // 首轮的 threadId 是后端生成的，就在这个事件里 ——
            // 它同时是下一轮、以及生成/提交工单要带的会话标识
            onWorkflow: (threadId) => setDraft((prev) => ({ ...prev, conversationId: threadId })),
            ...commonHandlers,
          })
        }
      } finally {
        setStreaming(false)
        abortRef.current = null
        // 中途自己中断的不算"没解析出选项"，别把它报成解析失败；
        // 追问、解决步骤、护栏拒绝和自助记录这几轮本来就没有也不该有选项，
        // 一条都没解析出来是正确结果
        setNoOption(
          !controller.signal.aborted &&
            received === 0 &&
            mode !== 'ask' &&
            mode !== 'refused' &&
            mode !== 'solution' &&
            mode !== 'probe' &&
            mode !== 'self_resolved',
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

    // 让滚动 effect 在这一帧忽略"用户是否贴着底部"：生成是用户主动点的，
    // 工单气泡必须立即出现在视野里。
    forceScrollRef.current = true

    setDraft((prev) => ({
      ...prev,
      workOrder: {
        status: 'generating',
        draft: null,
        roundIndex,
        optionId: option.id,
        selfResolved: false,
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
    !selfServiceDone &&
    !attachmentBlocking

  /**
   * 点自助决策卡片。
   *
   * 和普通意图选项不同：它不是"挑一个最像的"，而是一个明确的动作，
   * 所以不写 `selected`（那会冒出"生成工单"按钮），直接把动作发给后端。
   * 这轮的"用户发言"用卡片标题表示，对话记录才不会缺一环。
   */
  const runDecision = (option: IntentOption) => {
    const action = decisionActionOf(option)
    if (action === null || draft.conversationId === null || busy || submitted || sessionEnded) {
      return
    }
    void run(option.title, [], action)
  }

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
  /** 当前这一轮在等用户回答"自助有没有用"：卡片能点，也可以直接说 */
  const deciding = activeRound?.mode === 'decision'
  /** 发送按钮的文案；窄屏只留图标，这段仍作为无障碍名称 */
  const sendLabel = isFirst ? '开始分析' : asking ? '提交回答' : '继续分析'
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
    // 普通工单必须说得清是按哪条意图生成的；自助解决记录没有选项
    if (!generation.selfResolved && generation.optionId === null) {
      return
    }
    setSubmitting(true)
    try {
      const created = await submitWorkOrderDraft({
        conversationId: draft.conversationId,
        // 用生成时记下的选项编号，而不是"当前选中"：用户可能在生成后又点了别的卡片
        userSelect: generation.optionId ?? undefined,
        selfResolved: generation.selfResolved || undefined,
        title: values.title,
        type: values.type,
        priority: values.priority,
        problemDescription: values.problemDescription,
        originalProblemDescription: draftInputRounds(draft).join('\n'),
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
      message.success(generation.selfResolved ? '自助解决记录已提交' : '工单已提交')
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
   * 自助步骤的气泡数据。
   *
   * 流还在跑时先全部展开，结束后再折叠：否则每多流出一句就会把上一句顶掉，
   * 看起来像列表在跳。
   */
  /**
   * 出处链接。
   *
   * 两种情况不给链接：模型给的标题对不上原文（没有 id），或者当前用户没有知识库菜单——
   * 后者的路由压根没建，点过去只会看到"页面不存在"。
   */
  const openKnowledgeArticle = (articleId: string | null): (() => void) | undefined => {
    if (articleId === null || !canOpenKnowledge) {
      return undefined
    }
    return () => navigate(`${KNOWLEDGE_PATH}?openId=${encodeURIComponent(articleId)}`)
  }

  const solutionSteps = (round: IntentRound, active: boolean): SolutionSteps => ({
    ...parseSolution(round.solution),
    lead: round.lead !== '' ? round.lead : undefined,
    sources: round.sources.map((source) => ({
      ...source,
      onOpen: openKnowledgeArticle(source.articleId),
    })),
    collapsedCount: streaming && active ? undefined : COLLAPSED_STEPS,
  })

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

    if (round.mode === 'solution') {
      // 引导语比步骤先到：先显示这句话，别让用户对着空白等
      if (round.solution !== '' || round.lead !== '') {
        return { kind: 'steps', ...solutionSteps(round, isActive) }
      }
      return streaming && isActive
        ? { kind: 'pending', text: '正在查知识库…' }
        : null
    }

    if (round.mode === 'probe') {
      if (round.options.length === 0) {
        // 提问已经到了、卡片还在路上
        return round.question !== ''
          ? {
              kind: 'options',
              text: round.question,
              options: [],
              selectedId: null,
              disabled: true,
              hint: '正在准备确认选项…',
              onSelect: () => undefined,
            }
          : streaming && isActive
            ? { kind: 'pending', text: '正在查知识库…' }
            : null
      }
      return {
        kind: 'options',
        text: round.question !== '' ? round.question : undefined,
        plainCards: true,
        options: round.options,
        // 这些卡是"你遇到的是哪种现象"，不是"挑一个最像的"，没有选中态
        selectedId: null,
        disabled: !isActive || streaming || generating || submitted,
        hint: submitted ? '工单已创建，这段会话已结束' : PROBE_HINT,
        onSelect: (option) => {
          // 卡片文字本身就是一句回答（"是，就是纸卡住了"），当成用户发言发回去就行
          run(option.title, [])
        },
      }
    }

    if (round.mode === 'self_resolved') {
      // 记录本身渲染成所有轮次之后的那张工单卡片，这里只在等待时占位
      if (draft.workOrder !== null && draft.workOrder.status !== 'failed') {
        return null
      }
      return streaming && isActive
        ? { kind: 'pending', text: '正在整理自助解决记录…' }
        : null
    }

    const decision = round.mode === 'decision'
    if (round.options.length === 0) {
      // 自助步骤已经到了一部分、决策卡片还在路上：先把步骤显示出来，
      // 不要退回成"正在分析…"，那会让已经读过的内容闪一下消失
      if (decision && round.solution !== '') {
        return {
          kind: 'options',
          steps: solutionSteps(round, isActive),
          options: [],
          selectedId: null,
          disabled: true,
          hint: '正在准备下一步选项…',
          onSelect: () => undefined,
        }
      }
      return streaming && isActive ? { kind: 'pending', text: '正在分析…' } : null
    }

    const stateHint = submitted
      ? '工单已创建，这段会话已结束'
      : !isActive
        ? '这是之前某一轮的选项，仅供回看'
        : streaming
          ? `AI 正在继续给出选项，已收到 ${round.options.length} 条…`
          : generating
            ? '正在按选中的选项生成工单…'
            : undefined

    return {
      kind: 'options',
      // 自助解决步骤排在决策卡片上方：点「已经解决了」之前还能回看做法
      steps: decision && round.solution !== '' ? solutionSteps(round, isActive) : undefined,
      options: round.options,
      selectedId:
        draft.selected !== null && draft.selected.roundIndex === index && !decision
          ? draft.selected.option.id
          : null,
      // 流没结束时后面可能还有新选项，现在选中的编号随时会被打乱；
      // 工单已创建后整段会话只读
      disabled: !isActive || streaming || generating || submitted,
      hint: stateHint ?? (decision ? DECISION_HINT : OPTION_HINT),
      onSelect: (option) => {
        // 决策卡片是「走哪条路」，点了就直接发动作；普通选项只是先记下来
        if (decisionActionOf(option) !== null) {
          runDecision(option)
          return
        }
        setDraft((prev) => ({ ...prev, selected: { roundIndex: index, option } }))
      },
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
                src={AI_AVATAR}
                style={{ flexShrink: 0 }}
              />
              <div style={{ minWidth: 0 }}>
                <Text strong style={{ fontSize: token.fontSizeLG }}>
                  你好，我是智能提单助手
                </Text>
                <Paragraph style={{ margin: '6px 0 0' }}>
                  把遇到的问题告诉我就行：我来判断它属于哪一类工单 —— 信息够了，我会列出几种可能让你挑；
                  还缺关键信息，我会先反问你一句。如果知识库里有现成的办法，我会先给出自助解决的步骤；
                  按步骤解决不了，再帮你把工单填好，你可以点开工单修改并提交。
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

        {draft.rounds.map((round, index) => { // oxlint-disable-line react/refs -- 卡片点击是之后才执行的事件，不会在这次渲染里读 ref
          const bubble = aiBubble(round, index)
          // 开场白排在本轮作答前面：它就是"接住第一句话"的那一段，
          // 后面的追问/选项/步骤照旧渲染成各自的气泡
          const greeting =
            round.greeting === '' ? [] : splitBubbles({ kind: 'text', text: round.greeting })
          const contents = [...greeting, ...(bubble === null ? [] : splitBubbles(bubble))]
          return (
            <Fragment key={index}>
              <ChatBubble
                role="user"
                content={{ kind: 'text', text: round.input, attachments: round.attachments }}
              />
              {contents.length > 0 && (
                // 同一次作答拆出来的几句贴紧些（6 而不是 16），才看得出是连着说的
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {contents.map((content, lineIndex) => (
                    <ChatBubble
                      key={lineIndex}
                      role="ai"
                      content={content}
                      // 名字和模型标签只挂在第一条上，后面几条只对齐不重复
                      tag={lineIndex === 0 ? round.model : undefined}
                      continuation={lineIndex > 0}
                    />
                  ))}
                </div>
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
              selfResolved: draft.workOrder.selfResolved,
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

      {/* 工单创建后不再提供生成入口：同一段会话只出一张单。
          自助决策卡片不会被记成 selected，所以这里只会出现普通意图选项。 */}
      {selectedHere !== null && decisionActionOf(selectedHere) === null && !submitted && (
        <Button
          block
          size="large"
          type="primary"
          icon={<FileAddOutlined />}
          loading={generating}
          disabled={busy}
          onClick={onGenerate}
        >
          {generating
            ? '正在生成…'
            : draft.workOrder !== null && draft.workOrder.optionId === selectedHere.id
              ? // 这张单已经按当前选项生成过，再点就是换一版，而不是第一次生成
                '没填对？让 AI 重新总结一下'
              : `生成工单（#${selectedHere.id} ${selectedHere.title}）`}
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
            // 宽屏右边留出 78px 给字数（最长的 "2000/2000" 约 60px + 12px 间距）；
            // 窄屏不显示字数，省下的宽度留给输入框本身
            style={{ padding: narrow ? '8px 12px' : '8px 78px 8px 12px' }}
            value={composerValue}
            onChange={(event) => setComposerValue(event.target.value)}
            placeholder={composerPlaceholder({
              narrow,
              sessionEnded,
              selfServiceDone,
              submitted,
              isFirst,
              deciding,
              asking,
            })}
            // 单行起步，写长了自动长高；手机上限更低，别让输入框吃掉半个屏幕
            autoSize={{ minRows: 1, maxRows: narrow ? 4 : 6 }}
            maxLength={2000}
            disabled={busy || submitted || sessionEnded || selfServiceDone}
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
          {!narrow && (
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
          )}
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
              disabled={
                busy || submitted || sessionEnded || selfServiceDone || pending.length >= MAX_IMAGES
              }
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
            aria-label={sendLabel}
          >
            {narrow ? null : sendLabel}
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
        selfResolved={draft.workOrder?.selfResolved ?? false}
        originalDescription={draftInputRounds(draft).join('\n')}
        submitting={submitting}
        onClose={() => setDrawerOpen(false)}
        onChange={onDraftChange}
        onSubmit={(values) => void onSubmitWorkOrder(values)}
      />
    </Card>
  )
}
