import { buildUrl, getToken, notifyUnauthorized } from './http'

/** 与 http.ts 保持同一个头名：sa-token 的 token 头，取自后端 `sa-token.token-name`。 */
const TOKEN_HEADER = 'satoken'

/**
 * `start` 事件的数据。
 *
 * 两个接口的形状不完全一样，所以字段都是可选的：
 * - 辅助填单：`{ id, model, time }`
 * - 意图推断：`{ modelName, time }` —— 会话 id 改由随后的 `workflow` 事件给出
 */
export interface SseStartPayload {
  /** 会话 id（辅助填单仍从这里取） */
  id?: string
  /** 模型名。两种写法并存，取先有的那个 */
  model?: string
  modelName?: string
  time: number
}

/**
 * `chat` 事件的一帧：工作流某个节点的一次输出。
 *
 * `n` 是节点名 —— 同一轮里不会变，所以它在流里出现一次就能定下"这一轮是追问还是选项"。
 * `d` 的形状由节点决定：文字节点给 token 字符串，结构化节点给对象。
 */
export interface SseChatFrame {
  /** 节点名，如 `ask` / `options` */
  n?: string
  /** 该节点这一帧的数据 */
  d?: unknown
}

export interface SseHandlers {
  /**
   * 服务端生成（或回显）了会话 id。
   * 首轮调用必须把它存下来 —— 后续每一轮补充、以及最后提交工单都要带上它。
   */
  onStart?: (payload: SseStartPayload) => void
  /**
   * 工作流已经开始，这是本轮的 `threadId`。
   * 它同时是要带回去的会话标识 —— 下一轮、以及后续调用都得原样传它，流程才接得上。
   */
  onWorkflow?: (threadId: string) => void
  /**
   * 工作流节点的流式输出。文字节点的 `d` 是 token 片段（不是整行），
   * 拼接收在调用方那一层。
   */
  onChat?: (frame: SseChatFrame) => void
  /**
   * 一行完整文本。后端已经按 `\n` 切好了，这里收到的**不是** token 碎片，
   * 而是模型输出里的一整行。
   */
  onDelta?: (line: string) => void
  onDone?: (reason: string) => void
  /**
   * 站内信推送。数据是**一条消息的 JSON 原文**（形状与收件箱列表的元素相同），
   * 由调用方自己解析 —— 本模块不认识任何业务类型，`start` 会 `JSON.parse` 是因为
   * 那个形状（会话 id + 模型名）是 SSE 协议本身的一部分。
   *
   * 解析失败时调用方应当安静地丢掉这一帧：推来的这条和轮询拉到的会合并去重，
   * 丢一帧不会让消息消失。
   */
  onNotification?: (data: string) => void
  /** 流内业务错误、报文解析错误、网络错误都从这里出来 */
  onError?: (message: string) => void
}

export interface OpenSseOptions extends SseHandlers {
  /** 查询参数；值为 undefined 的项会被跳过 */
  query?: Record<string, string | number | undefined>
  /** 中断用。用户点「中断」时 abort 它 —— 否则流会一直读到模型把话说完 */
  signal?: AbortSignal
}

/**
 * 手写的 SSE 客户端。
 *
 * ## 为什么不能用 `EventSource`
 * 它**不支持自定义请求头**，带不上 `satoken`，所以每个请求都会 401。
 * 这条路堵死之后只剩 fetch。
 *
 * ## 为什么不复用 `http.ts` 的 `request()`
 * 那个函数会 `await response.text()` 再 `JSON.parse` 拆 RSM 信封，而 SSE 的响应体
 * 既不是 JSON 也不是信封，还永远不会结束 —— 在那里等 `text()` 就是永远等下去。
 *
 * ## 前导空格必须**恰好**丢一个
 * 后端 `Sse.standardize()` 在 data 前主动补了一个空格，用来抵消 SSE 规范
 * "客户端丢掉冒号后第一个空格"这条规则（否则模型输出的前导空格会消失，
 * 英文单词会粘成 `TheAPItest`）。所以这里的解析必须精确复刻那条规则：
 * `data:  abc` → 丢掉分隔的那一个 → `" abc"`（原文的前导空格保住了）。
 * 用 `trim()` 会把原文的前导空格一起吃掉，代码块的缩进就没了。
 *
 * ## 失败一律走 `onError`，这个 Promise 不 reject
 * 调用方只需要写一处错误处理。setup 阶段的失败（网络、非 2xx）也在内部转成 onError。
 */
export async function openSse(path: string, options: OpenSseOptions): Promise<void> {
  const { signal, onStart, onWorkflow, onChat, onDelta, onDone, onNotification, onError } = options
  const url = buildUrl(path, options.query)
  const fail = (message: string) => onError?.(message)

  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) {
    headers[TOKEN_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(url, { headers, signal })
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    fail('网络请求失败，请检查网络或稍后重试')
    return
  }

  if (response.status === 401) {
    // 和 request() 走同一套登出动作，否则会出现"一处跳登录、一处默默失败"
    notifyUnauthorized()
    fail('登录已过期，请重新登录')
    return
  }
  if (!response.ok) {
    // 鉴权失败（403）和后端异常走的是 RSM 的 JSON 信封，压根不是 SSE 流。
    // 当成流去解析只会得到一堆无从理解的噪音，所以这里按 JSON 读它的 msg。
    fail(await readErrorMessage(response))
    return
  }
  if (!response.body) {
    fail('响应没有正文，无法读取流')
    return
  }

  // ---- 报文解析 ----
  // 帧 = 若干字段行 + 一个空行。字段只有两个: event 和 data。
  let eventName = ''
  let dataLines: string[] = []

  const dispatch = () => {
    if (eventName !== '' || dataLines.length > 0) {
      handleFrame(eventName, dataLines.join('\n'))
    }
    eventName = ''
    dataLines = []
  }

  const handleLine = (line: string) => {
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) {
      // 注释行，SSE 里通常当心跳用
      return
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    // 规范要求：冒号后若有一个空格，那是分隔符，丢掉。只丢一个（见上面的说明）。
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      eventName = value.trim()
    } else if (field === 'data') {
      // 多行 data 用 \n 连起来，这是规范行为（后端不会发，但代理可能会重排）
      dataLines.push(value)
    }
  }

  const handleFrame = (event: string, data: string) => {
    switch (event) {
      case 'start':
        try {
          onStart?.(JSON.parse(data) as SseStartPayload)
        } catch {
          fail(`无法解析 start 事件：${data}`)
        }
        return
      case 'workflow': {
        try {
          const payload = JSON.parse(data) as { threadId?: unknown }
          // 没有 threadId 就当作没收到：后续轮次会退化成各说各的，但至少不会崩在这一帧上
          if (typeof payload.threadId === 'string' && payload.threadId !== '') {
            onWorkflow?.(payload.threadId)
          }
        } catch {
          fail(`无法解析 workflow 事件：${data}`)
        }
        return
      }
      case 'chat':
        try {
          onChat?.(JSON.parse(data) as SseChatFrame)
        } catch {
          fail(`无法解析 chat 事件：${data}`)
        }
        return
      case 'delta':
        onDelta?.(data)
        return
      case 'done':
        onDone?.(parseReason(data))
        return
      case 'notification':
        onNotification?.(data)
        return
      case 'error':
        fail(data || 'AI 服务返回了一个未知错误')
        return
      default:
        // 未知事件名（含无 event 的默认 message）直接忽略：协议以后加了新事件，
        // 老前端应当安静地跳过而不是报错
        return
    }
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let index: number
      while ((index = buffer.indexOf('\n')) !== -1) {
        let line = buffer.slice(0, index)
        buffer = buffer.slice(index + 1)
        // 后端不会发 \r，但经过某些代理可能会变成 CRLF；留着它会让 JSON 解析失败
        if (line.endsWith('\r')) {
          line = line.slice(0, -1)
        }
        handleLine(line)
      }
    }
    buffer += decoder.decode()
    if (buffer.length > 0) {
      handleLine(buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer)
    }
    // 流断了但最后一帧没有以空行收尾时，补一次 dispatch，否则最后一个事件会丢
    dispatch()
  } catch (error) {
    if (!isAbortError(error)) {
      fail(error instanceof Error ? error.message : '读取响应流失败')
    }
  } finally {
    reader.releaseLock()
  }
}

/** `done` 事件的数据是 `{"reason":"success"}`；解析不出来时给个空原因而不是崩掉。 */
function parseReason(data: string): string {
  try {
    const parsed = JSON.parse(data) as { reason?: string }
    return parsed.reason ?? ''
  } catch {
    return ''
  }
}

/** 非 2xx 的响应体是 RSM 的 JSON 信封，尽量把它的 msg 取出来给用户看。 */
async function readErrorMessage(response: Response): Promise<string> {
  let text = ''
  try {
    text = await response.text()
  } catch {
    // 读不出来就用状态码兜底
  }
  if (text !== '') {
    try {
      const parsed = JSON.parse(text) as { msg?: string }
      if (typeof parsed.msg === 'string' && parsed.msg !== '') {
        return parsed.msg
      }
    } catch {
      // 不是 JSON（比如网关自己的错误页），下面用状态码兜底
    }
  }
  return `请求失败（HTTP ${response.status}）`
}

/** abort 时 fetch 与 reader.read() 都会抛这个，它不是错误，是用户按了「中断」。 */
function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
