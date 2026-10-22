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

/** 修改自己的昵称。只需要登录态，不需要额外权限。 */
export function changeOwnNickname(nickname: string): Promise<void> {
  return request<void>('/api/auth/me/nickname', { method: 'PUT', body: { nickname } })
}

/**
 * 修改自己的密码。
 *
 * 成功后后端会注销该账号的全部会话（包括当前这个）。调用方要清 token 并回登录页，
 * 不要接着用旧 token 发请求。
 */
export function changeOwnPassword(oldPassword: string, newPassword: string): Promise<void> {
  return request<void>('/api/auth/me/password', { method: 'PUT', body: { oldPassword, newPassword } })
}
