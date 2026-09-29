import { request } from './http'
import type { CurrentUserContext, LoginResult } from '../types/api'

/**
 * 登录。对应后端 `POST /api/auth/acc/login`（@IsOpen）。
 * 成功后必须把 tokenValue 存起来，后续请求靠它。
 */
export function login(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>('/api/auth/acc/login', {
    method: 'POST',
    body: { username, password },
  })
}

/**
 * 拉当前用户上下文：用户信息 + 角色 + 权限键 + 可见菜单树。
 *
 * 后端对这个端点标的是 `@LoginOnly` —— 只要登录就能访问，不需要具体权限。
 * 这一点很关键：零角色的新用户也必须能拿到菜单，否则登录成功后界面直接白屏。
 */
export function fetchCurrentUser(): Promise<CurrentUserContext> {
  return request<CurrentUserContext>('/api/auth/me')
}

/** 登出 */
export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
