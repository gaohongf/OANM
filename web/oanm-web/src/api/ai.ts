import { openSse } from './sse'
import { normalizePriority, normalizeType } from './workOrder'
import type { WorkOrderPriority, WorkOrderType } from './workOrder'

/**
 * service-ai 的两个 SSE 接口：意图推断与辅助填单。
 *
 * ## 这里的核心是"容错解析"，不是 HTTP
 * 模型被要求只输出 JSONL，但它不保证守规矩：可能包一层 ```json 围栏、
 * 可能在某行后面多写一句解释、可能把该给英文枚举的地方写成中文。
 * 所以每一行都是**独立**解析的：一行坏掉只丢一行，页面继续渲染剩下的。
 * 这也正是"逐条渲染卡片"能成立的前提 —— 每解析出一条就立刻交给调用方。
 */

export const INTENT_INFERENCE_PATH = '/api/ai/assistant/wo/uiii'
export const ASSIST_SUBMIT_PATH = '/api/ai/assistant/wo/assist-submit'

/** 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。 */
export const AI_PERMISSIONS = {
  intentInference: 'GET:/api/ai/assistant/wo/uiii',
  assistSubmit: 'GET:/api/ai/assistant/wo/assist-submit',
} as const

// ---- 意图推断 ----

/**
 * 本轮由哪个节点作答。**直接是后端工作流里的节点名**，前端不另起一套叫法：
 *
 * - `ask`：缺时间/地点，这一轮只反问一句（`d` 是 token 片段）
 * - `options`：信息齐了，这一轮给选项（`d` 是一个选项对象）
 */
export type IntentNode = 'ask' | 'options'

export interface IntentOption {
  id: number
  title: string
  content: string
  /** 0-100 的整数。后端提示词里写的是字符串（`"80"`），这里统一成数字给界面用。 */
  probability: number
}

/**
 * 把一行文本解析成一个选项。
 *
 * @returns 不是选项（空行、围栏、散文）时返回 null
 */
export function parseIntentOption(line: string): IntentOption | null {
  const parsed = parseJsonLine(line)
  return parsed ? readOption(parsed) : null
}

/**
 * 从一段已经解析好的数据里读出一个选项。
 *
 * 字段值可能是模型编的（`"80%"`、`title` 给数字），所以一律过一遍归一化；
 * 缺 id 或 title 就不算一个选项 —— 那样的卡片既没法选也没法提交。
 */
function readOption(value: unknown): IntentOption | null {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  const parsed = value as Record<string, unknown>
  const id = toNumber(parsed.id)
  const title = toText(parsed.title)
  if (id === null || title === null) {
    return null
  }
  return {
    id,
    title,
    content: toText(parsed.content) ?? '',
    probability: toProbability(parsed.probability),
  }
}

/**
 * `chat` 帧里 `options` 节点的数据。
 *
 * 正常情况下后端给的是**已经解析好的对象**（`OptionsNode` 交出去的就是选项流）；
 * 万一以后改成逐字下发 JSON 文本，这里按 JSONL 行再兜一次，不必等前端报错才发现。
 */
function parseOptionData(data: unknown): IntentOption | null {
  return typeof data === 'string' ? parseIntentOption(data) : readOption(data)
}

// ---- 辅助填单 ----

/**
 * 一行输出对应的"往表单里填什么"。
 *
 * 做成判别联合而不是让调用方自己摸字段名，是因为**字段名是模型给的、不是我们能定的**：
 * `content` 要映射到 `problemDescription`、`solution_detail` 要映射到 `solutionDetail`、
 * `user_input_N` 要拆出轮次。把这层映射收在一个地方，页面里就只剩一个 switch。
 */
export type AssistLine =
  | { kind: 'field'; field: 'title' | 'problemDescription' | 'solutionDetail'; value: string }
  | { kind: 'type'; value: WorkOrderType }
  | { kind: 'priority'; value: WorkOrderPriority }
  | { kind: 'userChoose'; value: number }
  /** `user_input_N`：用户第 N 轮的原话 */
  | { kind: 'round'; index: number; value: string }
  /**
   * 值是模型编的，归一化不了（比如优先级给了个 `"P2"`）。
   * 只对 type/priority 产生 —— 它们对应表单里的下拉框，值非法时框会是空的，
   * 得让用户知道为什么。其它未知字段**直接忽略**：模型多给一个字段是无害的，
   * 为它弹个警告只是噪音。
   */
  | { kind: 'unrecognized'; key: string; raw: string }

const ROUND_KEY = /^user_input_(\d+)$/

/**
 * 把一行文本解析成一条填单指令。
 *
 * @returns 空行、围栏、散文、以及不认识的字段都返回 null
 */
export function parseAssistLine(line: string): AssistLine | null {
  const parsed = parseJsonLine(line)
  if (!parsed) {
    return null
  }
  // 一行只有一个字段，这是提示词约定的形状
  const key = Object.keys(parsed)[0]
  if (key === undefined) {
    return null
  }
  const raw = parsed[key]
  const text = toText(raw)
  const round = ROUND_KEY.exec(key)

  if (round) {
    // 一个 user_input_N 就是一轮 = 表单里的一行。模型要是把用户的换行转义进了值里，
    // 那一轮会占掉两行、后面的轮次整体错位（"第几行"就是"第几轮"）。
    // 压成空格，措辞不动 —— 和 aiSession.draftInputRounds 对本地那份输入做的事一样。
    return { kind: 'round', index: Number(round[1]), value: (text ?? '').replace(/[\r\n]+/g, ' ') }
  }

  switch (key) {
    case 'title':
      return text === null ? null : { kind: 'field', field: 'title', value: text }
    case 'content':
      return text === null ? null : { kind: 'field', field: 'problemDescription', value: text }
    case 'solution_detail':
      return text === null ? null : { kind: 'field', field: 'solutionDetail', value: text }
    case 'type': {
      const value = normalizeType(text)
      return value ? { kind: 'type', value } : { kind: 'unrecognized', key, raw: text ?? '' }
    }
    case 'priority': {
      const value = normalizePriority(text)
      return value ? { kind: 'priority', value } : { kind: 'unrecognized', key, raw: text ?? '' }
    }
    case 'user_choose': {
      const value = toNumber(raw)
      return value === null ? null : { kind: 'userChoose', value }
    }
    default:
      return null
  }
}

// ---- 流式调用 ----

export interface IntentStreamOptions {
  /** 首轮不传；用户补充时传上一轮 `workflow` 事件给的 threadId */
  conversationId?: string | null
  userInput: string
  signal?: AbortSignal
  /**
   * 本轮工作流的 threadId，首轮由后端生成。
   * 它就是要带回去的会话标识：下一轮、以及提交工单都得用它。
   */
  onWorkflow?: (threadId: string) => void
  /**
   * 本轮作答所用的模型名，来自 `start` 事件。页面把它标在气泡上，
   * 以后换成人工坐席时同一位置显示坐席标识。
   */
  onModel?: (modelName: string) => void
  /**
   * 本轮的作答形态，在第一个 `chat` 帧回来时触发一次。同一轮里不会变。
   * **没触发**说明这一轮什么都没产出（中断、空流），调用方按"选项"那套走即可。
   */
  onMode?: (node: IntentNode) => void
  /**
   * `ask` 路的追问。**每次给的都是拼到当前为止的整段文字**，不是 token 增量 ——
   * 拼接收在这一层，调用方直接 setState 即可。
   */
  onQuestion?: (text: string) => void
  /** 每解析出一个选项就回调一次 —— 卡片就是这样一条条出现的 */
  onOption: (option: IntentOption) => void
  onDone?: () => void
  /** 失败一律走这里，Promise 不会 reject */
  onError: (message: string) => void
}

export function startIntentInference(options: IntentStreamOptions): Promise<void> {
  /**
   * 本轮由哪个节点作答。null = 还没收到 `chat` 帧。
   * 没等到就一路按选项处理：空流或中断时页面至少是"这轮没给选项"，不会卡在未知状态。
   */
  let node: IntentNode | null = null
  /** `ask` 路的累积文本。攒够了整段再往外给，页面不用自己拼 token */
  let question = ''

  return openSse(INTENT_INFERENCE_PATH, {
    query: {
      // 首轮必须不带 id（后端会生成一个），而不是传空串 —— 空串会被当成一个真的会话 id
      id: options.conversationId ?? undefined,
      userInput: options.userInput,
    },
    signal: options.signal,
    // 两个字段名是历史差异（意图推断发 modelName，辅助填单发 model），取先有的那个
    onStart: (payload) => {
      const modelName = payload.modelName ?? payload.model
      if (modelName !== undefined && modelName !== '') {
        options.onModel?.(modelName)
      }
    },
    onWorkflow: (threadId) => options.onWorkflow?.(threadId),
    onChat: (frame) => {
      // 不认识节点名就安静跳过：以后工作流加节点，老前端应当少显示而不是报错
      if (frame.n !== 'ask' && frame.n !== 'options') {
        return
      }
      if (node === null) {
        node = frame.n
        options.onMode?.(frame.n)
      }
      if (frame.n === 'ask') {
        // 追问是纯文本 token，一个挨一个地拼 —— 它不是 JSON，别往 parseJsonLine 里送
        question += toText(frame.d) ?? ''
        options.onQuestion?.(question)
        return
      }
      const option = parseOptionData(frame.d)
      if (option) {
        options.onOption(option)
      }
    },
    onDone: () => options.onDone?.(),
    onError: options.onError,
  })
}

export interface AssistStreamOptions {
  conversationId: string
  /** 用户在意图推断页选中的选项编号 */
  userSelect: number
  signal?: AbortSignal
  onLine: (line: AssistLine) => void
  onRawLine?: (line: string) => void
  onDone?: () => void
  onError: (message: string) => void
}

export function startAssistSubmit(options: AssistStreamOptions): Promise<void> {
  return openSse(ASSIST_SUBMIT_PATH, {
    query: {
      id: options.conversationId,
      userSelect: options.userSelect,
    },
    signal: options.signal,
    onDelta: (line) => {
      options.onRawLine?.(line)
      const parsed = parseAssistLine(line)
      if (parsed) {
        options.onLine(parsed)
      }
    },
    onDone: () => options.onDone?.(),
    onError: options.onError,
  })
}

// ---- 逐行解析的公共部分 ----

/**
 * 从一行文本里抠出一个 JSON 对象。
 *
 * 逐条处理这几种不守规矩的写法：
 * - ```json 围栏（连同单独成行的 ``` 一起被吃掉）
 * - Markdown 列表符号 `- {"title": ...}`
 * - JSON 后面还跟着一句解释
 *
 * @returns 解析不出对象时返回 null
 */
function parseJsonLine(line: string): Record<string, unknown> | null {
  let text = line.trim()
  if (text === '') {
    return null
  }
  text = text
    .replace(/^```[a-zA-Z]*/, '')
    .replace(/```$/, '')
    .trim()
  if (text === '') {
    return null
  }
  text = text.replace(/^[-*]\s+/, '')

  // 只取第一个 { 到最后一个 } 之间的部分：模型常在 JSON 后面再写一句"以上就是全部选项"
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start === -1 || end <= start) {
    return null
  }

  try {
    const parsed: unknown = JSON.parse(text.slice(start, end + 1))
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    // 真解析不了就放弃这一行，调用方会把它留在"原始输出"里
    return null
  }
}

function toText(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }
  // 模型偶尔会把这些字段写成数字/布尔（比如 title 给了个数字）
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return null
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

/**
 * 概率收敛到 0-100 的整数。
 * 提示词说好了是 1-100 的整数，但实际见过 `"80%"` 这种带单位的值 ——
 * `Number()` 会给 NaN，那就按 0 处理而不是让 `Progress` 拿到 NaN。
 */
function toProbability(value: unknown): number {
  const parsed = toNumber(typeof value === 'string' ? value.replace('%', '') : value)
  if (parsed === null) {
    return 0
  }
  return Math.min(100, Math.max(0, Math.round(parsed)))
}
