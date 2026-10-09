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
  if (!parsed) {
    return null
  }
  const id = toNumber(parsed.id)
  const title = toText(parsed.title)
  // id 和 title 是这个选项的身份，缺了就没法渲染成一张能选的卡片
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
  /** 首轮不传；用户补充时传上一轮 `start` 事件给的那个 id */
  conversationId?: string | null
  userInput: string
  signal?: AbortSignal
  /** 拿到会话 id（首轮即由它产生）。后续每一轮和提交工单都要带上 */
  onStart?: (conversationId: string) => void
  /** 每解析出一个选项就回调一次 —— 卡片就是这样一条条出现的 */
  onOption: (option: IntentOption) => void
  /** 原始行，一行不漏。给"原始输出"面板用，模型不守格式时得能看见它到底发了什么 */
  onRawLine?: (line: string) => void
  onDone?: () => void
  /** 失败一律走这里，Promise 不会 reject */
  onError: (message: string) => void
}

export function startIntentInference(options: IntentStreamOptions): Promise<void> {
  return openSse(INTENT_INFERENCE_PATH, {
    query: {
      // 首轮必须不带 id（后端会生成一个），而不是传空串 —— 空串会被当成一个真的会话 id
      id: options.conversationId ?? undefined,
      userInput: options.userInput,
    },
    signal: options.signal,
    onStart: (payload) => options.onStart?.(payload.id),
    onDelta: (line) => {
      options.onRawLine?.(line)
      const option = parseIntentOption(line)
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
