import { request } from './http'
import type { PageResult } from '../types/api'

export interface CreateUserPayload {
  username: string
  password: string
}

/** 新建用户。后端只接收用户名和密码，角色走用户列表里的「分配角色」。 */
export function createUser(payload: CreateUserPayload): Promise<void> {
  return request<void>('/api/auth/users', { method: 'POST', body: payload })
}

/**
 * 封禁 / 解封用户。
 *
 * id 保持字符串：它是雪花 id，转成 number 会被取整成另一个值。
 */
export function updateUserLocked(id: string, locked: boolean): Promise<void> {
  return request<void>(`/api/auth/users/${id}/locked`, { method: 'PUT', body: { locked } })
}

/** 用户管理列表里的一行（后端 `UserAdminRes`）。 */
export interface AdminUserItem {
  /** 字符串形式的雪花 id */
  id: string
  username: string
  nickname: string
  locked: boolean
  roles: string[]
}

/**
 * 按权限查用户，用于「派发工单」的候选人下拉。
 *
 * size 取 200（后端 `PageRes.MAX_SIZE`）。当前体量下够用；候选人更多时应改成
 * 服务端关键字搜索，而不是继续调大这个数。
 */
export function listUsersByPermission(permissionKey: string): Promise<PageResult<AdminUserItem>> {
  return request<PageResult<AdminUserItem>>('/api/auth/users', {
    query: { current: 1, size: 200, permissionKey },
  })
}
