import type { IntentOption } from './ai'

/**
 * 本次提单的草稿：用户在意图推断页说过的原话、AI 给过的选项、选中的那一条。
 *
 * ## 为什么要存
 * 两件事都靠它：
 * 1. 填单页要拿"用户真实输入"（**以本地这份为准**，理由见下）
 * 2. 刷新页面不该丢掉前面几轮的输入
 *
 * ## 为什么以本地这份为准，而不是信模型转述的 `user_input_N`
 * 用户原话我们本来就有 —— 是他自己敲进输入框的。而模型转述会改写、会丢信息。
 * 所以模型那几行只用来**补本地缺失的轮次**（刷新后 session 丢了、或者用户直接
 * 拿着链接打开填单页时才用得上）。
 *
 * ## 为什么是 sessionStorage 而不是 localStorage
 * 这是**一次任务的草稿**，不是用户的偏好设置。下次打开浏览器时它还留着的话，
 * 用户会看到一个莫名其妙的、别人（上次的自己）填了一半的表单。
 * sessionStorage 的生命周期正好是"这个标签页"。
 *
 * ## 为什么只用一个槽位，而不是按 conversationId 分槽
 * 草稿属于"正在进行的那一次提单"，一个标签页里同时进行两次是没有意义的。
 * 单槽 + 把 conversationId 存在草稿内部，填单页不必先知道 id 就能读到草稿；
 * 而按 id 分槽的话，用链接直接打开（id 与草稿不匹配）就什么也读不到。
 * 两边 id 不一致时由调用方比对并决定是否采用，见 `matchesConversation`。
 */

const STORAGE_KEY = 'oanm.ai.draft'

/** 一轮：用户说的一句话 + AI 针对它给出的选项 */
export interface IntentRound {
  /** 用户这一轮的**真实输入**（不是模型转述） */
  input: string
  options: IntentOption[]
}

export interface IntentDraft {
  /** 首轮由后端生成，在 `start` 事件里回来。首轮流未开始时为 null */
  conversationId: string | null
  rounds: IntentRound[]
  selected: { roundIndex: number; option: IntentOption } | null
}

export function emptyDraft(): IntentDraft {
  return { conversationId: null, rounds: [], selected: null }
}

/**
 * 读取草稿。
 *
 * 读不到、或者形状不对（比如是旧版本写的）都返回 null —— 解析失败绝不能让
 * 填单页崩掉，最坏情况是回到"手工建单"模式，那是个可用状态。
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
    // 选中的那一条单独校验、单独丢弃：填单页要拿它的 option.id 去发起 AI 填写
    // （`selected.option.id` 是个会直接抛错的下标访问），而它坏掉的原因和轮次无关 ——
    // 一处坏掉不该把前面几轮的输入一起废掉。
    return { ...parsed, selected: isSelection(parsed.selected) ? parsed.selected : null }
  } catch {
    return null
  }
}

export function saveDraft(draft: IntentDraft): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft))
}

/** 提交成功后清掉，免得下次进填单页又是一张"上次的单" */
export function clearDraft(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

/**
 * 草稿里的用户真实输入，按轮次顺序。
 * 表单里"原始描述"那一段多行文本就是它拼出来的：一行 = 一轮。
 *
 * 轮内换行会被压成一个空格：这一段多行文本的**行号就是轮次编号**，用户在某轮输入框里
 * 敲了回车的话，那一轮就会占掉两行，后面的轮次整体错位 —— 而模型那边回传的
 * `user_input_N` 是按轮编号的，错位之后 N 会落到别人的行上，`applyLine` 按 index 补缺
 * 的那套逻辑就永远补不对。压掉换行是唯一能让"第 N 行 = 第 N 轮"成立的做法，
 * 措辞一个字没动。
 */
export function draftInputRounds(draft: IntentDraft | null): string[] {
  if (!draft) {
    return []
  }
  return draft.rounds.map((round) => round.input.replace(/[\r\n]+/g, ' '))
}

/** 草稿是不是属于这个会话。用链接直接打开填单页时靠它判断能不能沿用草稿。 */
export function matchesConversation(draft: IntentDraft | null, conversationId: string | null): boolean {
  if (!draft || conversationId === null) {
    return false
  }
  return draft.conversationId === conversationId
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
function isDraft(value: unknown): value is IntentDraft {
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
