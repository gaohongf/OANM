import { readWorkOrderDraft } from './ai'
import type { IntentOption, KnowledgeSource, WorkOrderDraft } from './ai'
import type { UploadedFile } from './files'

/**
 * 本次提单的草稿：用户在智能提单页说过的原话、AI 给过的选项、选中的那一条，
 * 以及最后生成出来的工单。
 *
 * ## 为什么要存
 * 1. 刷新页面不该丢掉前面几轮的输入和已经生成好的工单；
 * 2. 提交时"原始描述"要用**用户原话**（以本地这份为准，理由见 `draftInputRounds`）。
 *
 * ## 为什么是 sessionStorage 而不是 localStorage
 * 这是**一次任务的草稿**，不是用户的偏好设置。下次打开浏览器时它还留着的话，
 * 用户会看到一个莫名其妙的、别人（上次的自己）填了一半的表单。
 * sessionStorage 的生命周期正好是"这个标签页"。
 *
 * ## 为什么只用一个槽位，而不是按 conversationId 分槽
 * 草稿属于"正在进行的那一次提单"，一个标签页里同时进行两次是没有意义的。
 */

const STORAGE_KEY = 'oanm.ai.draft'

/**
 * 这一轮由哪个节点作答 —— 直接沿用后端工作流里的节点名，
 * 前端不另起一套叫法，免得两边各维护一份映射又慢慢漂开。
 *
 * `PENDING` 是前端自己的状态：请求发出去了、`chat` 帧还没回来。
 * `refused` 沿用护栏节点前后端的约定：这一轮输入被拦下，只有一句拒绝说明。
 */
export type IntentRoundMode =
  | 'PENDING'
  | 'ask'
  | 'options'
  | 'solution'
  | 'decision'
  | 'probe'
  | 'self_resolved'
  | 'refused'

/** 一轮：用户说的一句话 + AI 针对它的作答（一组选项、一句追问，或一句拒绝） */
export interface IntentRound {
  /** 用户这一轮的**真实输入**（不是模型转述） */
  input: string
  /**
   * AI 以什么形态作答。
   *
   * `PENDING` 只在流的前几百毫秒存在（从发起到收到路由帧），
   * 界面靠它区分"正在分析"和"给了选项"——否则那段时间会显示成"没给出选项"。
   */
  mode: IntentRoundMode
  /**
   * 这一轮由谁作答 —— 目前是模型名（`start` 事件里的 `modelName`），显示在气泡名字旁边。
   * 为以后的人工介入留位：轮到坐席接手时，这里放坐席标识，展示位置不变。
   * 空字符串表示还不知道（旧草稿、或流在 `start` 之前就断了）。
   */
  model: string
  /**
   * 会话第一轮的开场白（"收到，你说的打印机出不了纸"）。
   * 它不改变本轮形态，排在本轮作答那条气泡前面；后续轮次为空字符串。
   */
  greeting: string
  /** `mode === 'ask'` 时 AI 追问的原文（纯文本，带换行） */
  question: string
  /**
   * 自助步骤之前的一句引导（"可以先按下面这几步试试"）。
   * 模型没给时是空字符串，界面直接省略这一句。
   */
  lead: string
  /**
   * 自助解决步骤的原文（纯文本，步骤之间用换行分隔）。
   * `solution` / `decision` 两种模式都用它：决策卡片展示在步骤下方。
   */
  solution: string
  /**
   * 自助建议引用到的知识库文章（带文章 id，可点开原文）。
   * 刷新后仍能点击，所以和轮次一起存。
   */
  sources: KnowledgeSource[]
  /** `mode === 'refused'` 时护栏给出的拒绝说明（纯文本） */
  refusal: string
  /**
   * 护栏判定这段会话已结束（无关输入累计超限）。
   * 一旦为 true，页面不再允许继续输入，只能「重新开始」。
   */
  ended: boolean
  options: IntentOption[]
  /**
   * 用户这一轮附带的图片（已经上传成功，只存地址和元信息）。
   *
   * 草稿整体走 `JSON.stringify`，存不了 `File`/`Blob` —— 所以选图后先上传，
   * 这里只留上传结果。刷新页面后缩略图还能照常显示。
   */
  attachments: UploadedFile[]
}

/**
 * 最近一次工单生成的状态。它不是某一轮的作答，而是整段对话的产物 ——
 * 所以挂在草稿上、在所有轮次之后单独渲染一条气泡。
 */
export interface WorkOrderGeneration {
  /**
   * `generating` 时前端显示骨架；`ready` 显示卡片、可打开抽屉；
   * `failed` 显示失败原因；`submitted` 显示工单号。
   */
  status: 'generating' | 'ready' | 'failed' | 'submitted'
  /** 模型整理出来的工单（用户可能在抽屉里改过，改完会回写到这里） */
  draft: WorkOrderDraft | null
  /**
   * 生成时选中的是哪一轮、哪一项 —— 提交归档用它，而不是"当前选中"：
   * 用户完全可能在生成之后又去点了别的选项卡片。
   */
  roundIndex: number
  /** 生成时选中的选项编号；自助解决记录没有选项，为 null */
  optionId: number | null
  /** true 表示这是一条自助解决记录，而不是待受理的工单 */
  selfResolved: boolean
  /** 生成所用的模型名，气泡标签用 */
  model: string
  workOrderId: string | null
  error: string | null
}

type StoredRound = Pick<IntentRound, 'input' | 'options'> &
  Partial<
    Pick<
      IntentRound,
      | 'mode'
      | 'model'
      | 'greeting'
      | 'question'
      | 'lead'
      | 'solution'
      | 'sources'
      | 'refusal'
      | 'ended'
      | 'attachments'
    >
  >

/**
 * 校验用的"宽松草稿"：旧版本没有 `workOrder` 字段，校验不该因此把整份草稿判死。
 */
type StoredDraft = Omit<IntentDraft, 'rounds' | 'workOrder'> & {
  rounds: StoredRound[]
  workOrder?: unknown
}

export interface IntentDraft {
  /** 工作流的 threadId。首轮由后端生成，在 `workflow` 事件里回来；流未开始时为 null */
  conversationId: string | null
  rounds: IntentRound[]
  selected: { roundIndex: number; option: IntentOption } | null
  /** 最近一次工单生成；还没生成过时为 null */
  workOrder: WorkOrderGeneration | null
}

export function emptyDraft(): IntentDraft {
  return { conversationId: null, rounds: [], selected: null, workOrder: null }
}

/**
 * 读取草稿。
 *
 * 读不到、或者形状不对（比如是旧版本写的）都返回 null —— 解析失败绝不能让页面崩掉，
 * 最坏情况是回到空白会话，那是个可用状态。
 */
export function loadDraft(): IntentDraft | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (raw === null) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!isDraft(parsed)) {
      return null
    }
    // 选中的那一条单独校验、单独丢弃：`selected.option.id` 是个会直接抛错的下标访问，
    // 而它坏掉的原因和轮次无关 —— 一处坏掉不该把前面几轮的输入一起废掉。
    return {
      conversationId: parsed.conversationId,
      rounds: parsed.rounds.map(normalizeRound),
      selected: isSelection(parsed.selected) ? parsed.selected : null,
      workOrder: normalizeWorkOrder(parsed.workOrder),
    }
  } catch {
    return null
  }
}

/**
 * 把存下来的轮次补成完整的形状。
 *
 * - 旧草稿里的 `OPTIONS` / `PROBLEM` 是上一版前端自己起的名字（那时后端发的是
 *   `routing_type` 帧）。翻成现在的节点名，否则用户刷新后会把一句追问显示成"没有选项"。
 * - `PENDING` 只可能来自"流式输出到一半时刷新了"，而刷新之后没有任何流在跑，
 *   转圈会永远转下去。落到 `options` 分支，用户看到的是可操作的提示而不是假死。
 */
function normalizeRound(round: StoredRound): IntentRound {
  const mode = normalizeMode(round.mode)
  return {
    input: round.input,
    mode,
    model: typeof round.model === 'string' ? round.model : '',
    greeting: typeof round.greeting === 'string' ? round.greeting : '',
    question: typeof round.question === 'string' ? round.question : '',
    lead: typeof round.lead === 'string' ? round.lead : '',
    solution: typeof round.solution === 'string' ? round.solution : '',
    sources: normalizeSources(round.sources),
    refusal: typeof round.refusal === 'string' ? round.refusal : '',
    ended: round.ended === true,
    options: mode === 'decision' ? round.options : withoutActions(round.options),
    attachments: normalizeAttachments(round.attachments),
  }
}

/**
 * 普通意图/故障确认的选项不该带 `action`：它是"点下去直接执行"的指令，
 * 只属于自助决策卡片。旧草稿里可能留着模型乱填的 WORK_ORDER，这里清掉，
 * 否则刷新后这些卡片仍会显示成决策卡，点了还会把选项重新生成一遍。
 */
function withoutActions(options: IntentOption[]): IntentOption[] {
  return options.map((option) => (option.action === null ? option : { ...option, action: null }))
}

/**
 * 出处逐条校验、逐条丢弃。
 *
 * `title` 是唯一必须的字段：没有它就是一行空的"参考知识"。
 * 旧草稿里根本没有这个字段，`articleId` 缺失也只意味着点不开，所以照样保留。
 */
function normalizeSources(value: unknown): KnowledgeSource[] {
  if (!Array.isArray(value)) {
    return []
  }
  const sources: KnowledgeSource[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) {
      continue
    }
    const candidate = item as Record<string, unknown>
    if (typeof candidate.title !== 'string' || candidate.title.trim() === '') {
      continue
    }
    sources.push({
      articleId:
        typeof candidate.articleId === 'string' && candidate.articleId !== ''
          ? candidate.articleId
          : null,
      title: candidate.title.trim(),
    })
  }
  return sources
}

/**
 * 附件逐项校验、逐项丢弃。
 *
 * `url` 是唯一必须的字段：没有它就显示不了图。其余字段缺了用空值补，
 * 界面最多是文件名空着，不至于让整轮对话作废。
 */
function normalizeAttachments(value: unknown): UploadedFile[] {
  if (!Array.isArray(value)) {
    return []
  }
  const attachments: UploadedFile[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) {
      continue
    }
    const candidate = item as Record<string, unknown>
    if (typeof candidate.url !== 'string' || candidate.url === '') {
      continue
    }
    attachments.push({
      id: typeof candidate.id === 'string' ? candidate.id : '',
      url: candidate.url,
      filename: typeof candidate.filename === 'string' ? candidate.filename : '',
      originalFilename:
        typeof candidate.originalFilename === 'string' ? candidate.originalFilename : '',
      size: typeof candidate.size === 'number' ? candidate.size : 0,
      contentType: typeof candidate.contentType === 'string' ? candidate.contentType : '',
    })
  }
  return attachments
}

function normalizeMode(value: unknown): IntentRoundMode {
  if (value === 'ask' || value === 'PROBLEM') {
    return 'ask'
  }
  if (value === 'refused') {
    return 'refused'
  }
  if (
    value === 'solution' ||
    value === 'decision' ||
    value === 'probe' ||
    value === 'self_resolved'
  ) {
    return value
  }
  // 旧草稿只有 options；未知值也落到这里，至少卡片还能点
  return 'options'
}

/**
 * 校验并归还工单生成状态。
 *
 * 刷新时正在生成的那一次必须降级成 `failed`：刷新之后没有任何流在跑，
 * 骨架会永远转下去，而用户以为 AI 还在干活。
 *
 * 缺 `roundIndex` 的整块丢弃 —— 没有它就没法把草稿归档到对应会话。
 * 普通工单还必须有 `optionId`；自助解决记录没有选项，`optionId` 允许为 null。
 */
function normalizeWorkOrder(value: unknown): WorkOrderGeneration | null {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  const item = value as Record<string, unknown>
  const status = item.status
  if (
    status !== 'generating' &&
    status !== 'ready' &&
    status !== 'failed' &&
    status !== 'submitted'
  ) {
    return null
  }
  if (typeof item.roundIndex !== 'number') {
    return null
  }
  const selfResolved = item.selfResolved === true
  const optionId = typeof item.optionId === 'number' ? item.optionId : null
  // 普通工单必须有选项编号，否则提交时说不出这张单是按哪条意图生成的；
  // 自助解决记录本来就没有选项。
  if (!selfResolved && optionId === null) {
    return null
  }

  const draft = readWorkOrderDraft(item.draft)
  const base = {
    draft,
    roundIndex: item.roundIndex,
    optionId,
    selfResolved,
    model: typeof item.model === 'string' ? item.model : '',
    workOrderId: typeof item.workOrderId === 'string' ? item.workOrderId : null,
    error: typeof item.error === 'string' ? item.error : null,
  }

  if (status === 'generating') {
    return { ...base, status: 'failed', error: '页面刷新中断了这次生成，请重新生成' }
  }
  if (status === 'ready' && draft === null) {
    return null
  }
  if (status === 'submitted' && (base.workOrderId === null || base.workOrderId === '')) {
    return null
  }
  return { ...base, status }
}

export function saveDraft(draft: IntentDraft): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft))
}

/** 用户点「重新开始」时清掉，免得下次进来还是上一单的内容 */
export function clearDraft(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

/**
 * 草稿里的用户真实输入，按轮次顺序。
 * 表单里"原始描述"那一段多行文本就是它拼出来的：一行 = 一轮。
 *
 * 轮内换行会被压成一个空格：这一段多行文本的**行号就是轮次编号**，
 * 用户在某轮输入框里敲了回车的话，那一轮就会占掉两行，后面的轮次整体错位。
 * 措辞一个字没动。
 */
export function draftInputRounds(draft: IntentDraft | null): string[] {
  if (!draft) {
    return []
  }
  return draft.rounds.map((round) => round.input.replace(/[\r\n]+/g, ' '))
}

/**
 * 选中的那一条选项的形状校验。
 *
 * `id` 和 `title` 是必须的：前者要被拼进接口参数（`option.id`），后者是界面上显示
 * "已选中什么"用的。`content`/`probability` 缺了顶多是卡片显示得难看，不值得因此丢弃。
 */
function isSelection(value: unknown): value is IntentDraft['selected'] {
  if (value === null) {
    return true
  }
  if (typeof value !== 'object') {
    return false
  }
  const item = value as Record<string, unknown>
  if (typeof item.roundIndex !== 'number') {
    return false
  }
  const option = item.option
  if (typeof option !== 'object' || option === null) {
    return false
  }
  const candidate = option as Record<string, unknown>
  return typeof candidate.id === 'number' && typeof candidate.title === 'string'
}

/** 形状校验。sessionStorage 里的东西可能是上个版本写的，不能当作可信输入。 */
function isDraft(value: unknown): value is StoredDraft {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  if (candidate.conversationId !== null && typeof candidate.conversationId !== 'string') {
    return false
  }
  if (!Array.isArray(candidate.rounds)) {
    return false
  }
  return candidate.rounds.every((round) => {
    if (typeof round !== 'object' || round === null) {
      return false
    }
    const item = round as Record<string, unknown>
    return typeof item.input === 'string' && Array.isArray(item.options)
  })
}
