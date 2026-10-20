import { request } from './http'
import { openSse } from './sse'

/**
 * service-ai 的两步：意图推断与工单生成。
 *
 * 两步走同一个 SSE 端点（`/api/ai/assistant/wo/uiii`）：生成就是这段对话的下一轮，
 * 客户端带上同一个会话 id 和一个选项编号继续即可。提交建单是普通 JSON 接口，
 * 由后端代用户转发给工单服务。
 *
 * ## 意图推断仍是"容错解析"
 * 模型被要求只输出 JSONL，但它不保证守规矩：可能包一层 ```json 围栏、
 * 可能在某行后面多写一句解释、可能把该给英文枚举的地方写成中文。
 * 所以每一行都是**独立**解析的：一行坏掉只丢一行，页面继续渲染剩下的。
 *
 * ## 工单生成不再逐行
 * 它一次返回一个完整的 `draft` 对象（后端 `.entity()` 结构化输出），
 * 前端只在气泡里先放骨架、拿到对象后整体渲染。
 */

export const INTENT_INFERENCE_PATH = '/api/ai/assistant/wo/uiii'
export const WORK_ORDER_SUBMIT_PATH = '/api/ai/assistant/wo/submit'

/**
 * 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。
 *
 * 没有提交接口的键：它是 `@LoginOnly`，真正的门禁是工单服务的
 * `POST:/api/ops/work_order`。
 */
export const AI_PERMISSIONS = {
  intentInference: 'GET:/api/ai/assistant/wo/uiii',
} as const

// ---- 意图推断 ----

/**
 * 本轮由哪个节点作答。**直接是后端工作流里的节点名**，前端不另起一套叫法：
 *
 * - `ask`：缺时间/地点，这一轮只反问一句（`d` 是 token 片段）
 * - `options`：信息齐了，这一轮给选项（`d` 是一个选项对象）
 * - `refused`：输入与运维无关，这一轮被护栏拦下，只输出一句拒绝说明
 *
 * 识图（`vision` / `again_vision`）不在协议里：识图结果留在后端，只用于
 * 生成追问和提取工单信息，不作为 SSE 帧下发。
 */
export type IntentNode = 'ask' | 'options' | 'refused'

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

// ---- 工单草稿 ----

/**
 * 模型整理出来的一张工单。
 *
 * `type` / `priority` 保留模型给的**原始字符串**：提示词约束了取值，但模型仍可能
 * 回中文或大小写不一。展示与预填时再用 `normalizeType` / `normalizePriority` 归一化 ——
 * 归一化失败时界面如实显示原值、下拉框留空让用户自己选，而不是把整张单判死。
 */
export interface WorkOrderDraft {
  title: string
  type: string
  priority: string
  problemDescription: string
  solutionDetail: string
}

/**
 * 从 `draft` 帧的数据里读出草稿。
 *
 * `title` 与 `problemDescription` 是提交的必填项，缺任意一个就不算生成成功 ——
 * 让用户打开一张只有类型和优先级的表单，不如直接告诉他这次没生成出来、再点一次。
 * 其余字段缺了只是表单里空着。
 */
export function readWorkOrderDraft(value: unknown): WorkOrderDraft | null {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  const parsed = value as Record<string, unknown>
  const title = (toText(parsed.title) ?? '').trim()
  const problemDescription = (toText(parsed.problemDescription) ?? '').trim()
  if (title === '' || problemDescription === '') {
    return null
  }
  return {
    title,
    type: toText(parsed.type) ?? '',
    priority: toText(parsed.priority) ?? '',
    problemDescription,
    solutionDetail: toText(parsed.solutionDetail) ?? '',
  }
}

// ---- 流式调用 ----

export interface IntentStreamOptions {
  /** 首轮不传；用户补充时传上一轮 `workflow` 事件给的 threadId */
  conversationId?: string | null
  userInput: string
  /**
   * 本轮附带的图片访问地址（`uploadFile` 返回的相对地址）。
   * 后端据此读取图片字节交给模型识图；识图结果留在后端，只体现为后续的追问/选项。
   * 超过 3 张后端会直接拒绝。
   */
  images?: string[]
  signal?: AbortSignal
  /**
   * 本轮工作流的 threadId，首轮由后端生成。
   * 它就是要带回去的会话标识：下一轮、以及生成/提交工单都得用它。
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
  /**
   * 护栏拦下本轮输入时的拒绝说明，同样是拼好的整段文字。
   * `ended === true` 表示违规次数超限、会话已被主动结束 —— 调用方应当禁止继续输入。
   */
  onGuardrail?: (text: string, ended: boolean) => void
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
  /** 护栏拒绝说明的累积文本 */
  let refusal = ''

  return openSse(INTENT_INFERENCE_PATH, {
    query: {
      // 首轮必须不带 id（后端会生成一个），而不是传空串 —— 空串会被当成一个真的会话 id
      id: options.conversationId ?? undefined,
      userInput: options.userInput,
      // 用逗号拼成一个参数：后端 @RequestParam List<String> 会自动按逗号拆分。
      // 图片地址是 /file/yyyy/MM/dd/<uuid>.jpg，本身不含逗号。
      images: options.images?.length ? options.images.join(',') : undefined,
    },
    signal: options.signal,
    onStart: (payload) => {
      const modelName = payload.modelName ?? payload.model
      if (modelName !== undefined && modelName !== '') {
        options.onModel?.(modelName)
      }
    },
    onWorkflow: (threadId) => options.onWorkflow?.(threadId),
    onChat: (frame) => {
      // 护栏拦下输入：这一轮没有别的节点会跑，它就是本轮的"作答"
      if (frame.n === 'guardrail_reject' || frame.n === 'guardrail_end') {
        if (node === null) {
          node = 'refused'
          options.onMode?.(node)
        }
        refusal += toText(frame.d) ?? ''
        options.onGuardrail?.(refusal, frame.n === 'guardrail_end')
        return
      }
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

export interface DraftStreamOptions {
  /** 生成必须发生在已有会话上：后端要从那个 threadId 的中断点恢复 */
  conversationId: string
  /** 用户在意图推断页选中的选项编号 */
  userSelect: number
  signal?: AbortSignal
  onModel?: (modelName: string) => void
  /** 生成成功时回调一次，拿到的是完整草稿 */
  onDraft: (draft: WorkOrderDraft) => void
  onDone?: () => void
  /** 失败一律走这里，Promise 不会 reject */
  onError: (message: string) => void
}

/**
 * 让模型把选中的意图整理成一张工单。
 *
 * 同一个端点、同一段会话，只是这次带的是 `userSelect` 而不是 `userInput`。
 * 输出仍是 SSE（这样错误、模型名、结束原因跟意图推断走同一套协议），
 * 但业务数据只有一帧 `chat { n: "draft", d: {...} }`。
 */
export function generateWorkOrderDraft(options: DraftStreamOptions): Promise<void> {
  return openSse(INTENT_INFERENCE_PATH, {
    query: {
      id: options.conversationId,
      userSelect: options.userSelect,
    },
    signal: options.signal,
    onStart: (payload) => {
      const modelName = payload.modelName ?? payload.model
      if (modelName !== undefined && modelName !== '') {
        options.onModel?.(modelName)
      }
    },
    onChat: (frame) => {
      if (frame.n !== 'draft') {
        return
      }
      const draft = readWorkOrderDraft(frame.d)
      if (draft) {
        options.onDraft(draft)
      }
    },
    onDone: () => options.onDone?.(),
    onError: options.onError,
  })
}

// ---- 提交 ----

export interface SubmitDraftPayload {
  conversationId: string
  userSelect: number
  title: string
  type: string
  priority: string
  problemDescription: string
  /** 用户原话，一行一轮 */
  originalProblemDescription?: string
  solutionDetail?: string
}

/**
 * 提交建单。
 *
 * 请求发给 service-ai，由它带着当前用户的 `satoken` 转发给工单服务 ——
 * 权限校验、`createBy` 审计、建单通知仍在工单服务那一条既有路径上。
 */
export function submitWorkOrderDraft(payload: SubmitDraftPayload): Promise<{ id: string }> {
  return request<{ id: string }>(WORK_ORDER_SUBMIT_PATH, { method: 'POST', body: payload })
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
    // 真解析不了就放弃这一行，就当它没出现过
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
