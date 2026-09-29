import { create } from 'zustand'
import * as authApi from '../api/auth'
import { clearToken, getToken, setToken, setUnauthorizedHandler } from '../api/http'
import { hasPermission, isSuperUser } from '../permission/match'
import type { CurrentUser, MenuNode } from '../types/api'

interface AuthState {
  /** 是否还在判断登录态。为 true 时不渲染路由，避免"先闪登录页再跳回来" */
  initializing: boolean
  user: CurrentUser | null
  roles: string[]
  permissions: string[]
  menus: MenuNode[]

  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** 用本地 token 拉一次上下文；无 token 或失败则视为未登录 */
  bootstrap: () => Promise<void>
  /** token 失效时由 http 层回调 */
  handleUnauthorized: () => void
  /** 判断当前用户是否具备某权限键 */
  can: (required: string) => boolean
}

export const useAuthStore = create<AuthState>((set, get) => ({
  initializing: true,
  user: null,
  roles: [],
  permissions: [],
  menus: [],

  async login(username, password) {
    const result = await authApi.login(username, password)
    // 先存 token 再拉上下文 —— /me 需要带上它
    setToken(result.tokenValue)
    const context = await authApi.fetchCurrentUser()
    set({
      initializing: false,
      user: context.user,
      roles: context.roles,
      permissions: context.permissions,
      menus: context.menus,
    })
  },

  async logout() {
    try {
      await authApi.logout()
    } catch {
      // 后端登出失败（例如 token 已过期）不该让前端卡在登录态 ——
      // 本地状态该清还是要清，否则用户点"退出"没反应。
    }
    clearToken()
    get().handleUnauthorized()
  },

  async bootstrap() {
    if (!getToken()) {
      // 没有 token 就没什么可拉的，直接进入未登录状态。
      // 关键是这一步<b>不发请求</b>：否则每个未登录用户都会先吃一个 401。
      set({ initializing: false })
      return
    }

    try {
      const context = await authApi.fetchCurrentUser()
      set({
        initializing: false,
        user: context.user,
        roles: context.roles,
        permissions: context.permissions,
        menus: context.menus,
      })
    } catch {
      // token 存在但已失效（或后端不可用）—— 当作未登录处理。
      // 不在这里 clearToken：http 层遇到 401 已经清过了，而网络故障时保留 token
      // 反而更好（用户刷新一下就能恢复，不用重新登录）。
      set({ initializing: false, user: null, roles: [], permissions: [], menus: [] })
    }
  },

  handleUnauthorized() {
    set({ user: null, roles: [], permissions: [], menus: [], initializing: false })
  },

  can(required) {
    return hasPermission(get().permissions, required)
  },
}))

/** 是否是超级管理员。菜单与按钮在超管下应当全部可见。 */
export function useIsSuperUser(): boolean {
  return useAuthStore((state) => isSuperUser(state.permissions))
}

// 把 401 的处理接到 http 层。放在模块顶层是因为它必须在任何请求发出之前注册好。
setUnauthorizedHandler(() => {
  useAuthStore.getState().handleUnauthorized()
})
