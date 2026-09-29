import type { RsmResponse } from '../types/api'

/** sa-token 的 token 头名。取自后端 `sa-token.token-name`，默认就是 satoken。 */
const TOKEN_HEADER = 'satoken'

const TOKEN_STORAGE_KEY = 'oanm.token'

/** 收到 401 时的回调，由 auth store 注册（避免这里直接依赖 store 造成循环引用）。 */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

/**
 * 业务错误。
 *
 * 后端用"HTTP 200 + type=ERROR"表达业务失败（这是 RSM 的统一响应约定），
 * 所以调用方不能只看 HTTP 状态码 —— 必须检查 type。把它包装成异常，
 * 是为了让业务代码用 try/catch 而不是层层判断返回值。
 */
export class ApiError extends Error {
  readonly code: number
  readonly httpStatus: number

  constructor(message: string, code: number, httpStatus: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** 附加查询参数；值为 undefined 的项会被跳过 */
  query?: Record<string, string | number | undefined>
}

/**
 * 发起一次 API 请求并拆掉 RSM 信封，直接返回 `data`。
 *
 * 所有后端接口都走这里，所以"怎么判成功、怎么带 token、401 怎么办"只有一份实现。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = buildUrl(path, options.query)

  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) {
    headers[TOKEN_HEADER] = token
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    // fetch 只在网络层失败时 reject（断网、DNS、连接被拒）。业务错误不会走到这里。
    throw new ApiError('网络请求失败，请检查网络或稍后重试', -1, 0)
  }

  if (response.status === 401) {
    // 未登录/登录过期。清掉本地 token 并通知上层跳登录页 ——
    // 不做的话用户会停在一个"所有请求都失败"的界面上，不知道为什么。
    clearToken()
    onUnauthorized?.()
    throw new ApiError('登录已过期，请重新登录', 401, 401)
  }

  // 有些接口是 @IsOpen 且返回裸字符串（如登出），没有信封可拆
  const text = await response.text()
  if (text === '') {
    return undefined as T
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new ApiError(`响应不是合法 JSON（HTTP ${response.status}）`, -1, response.status)
  }

  if (!isRsmResponse(parsed)) {
    throw new ApiError(`响应格式不符合预期（HTTP ${response.status}）`, -1, response.status)
  }

  if (parsed.type !== 'SUCCESS') {
    throw new ApiError(parsed.msg || '请求失败', parsed.code, response.status)
  }

  return parsed.data as T
}

function buildUrl(path: string, query?: Record<string, string | number | undefined>): string {
  if (!query) {
    return path
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    // 跳过 undefined：URLSearchParams 会把它们变成字面量 "undefined" 发给后端
    if (value !== undefined) {
      params.append(key, String(value))
    }
  }
  const queryString = params.toString()
  return queryString ? `${path}?${queryString}` : path
}

/** 运行时校验信封形状。用户输入不可信，服务端的响应同样不可假定。 */
function isRsmResponse(value: unknown): value is RsmResponse<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.type === 'string' && typeof candidate.code === 'number'
}
