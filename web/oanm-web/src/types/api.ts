/**
 * 后端统一响应与领域类型。
 */

/**
 * 后端 RSM 的统一响应包装（对应 Java 侧的 `Response{code, data, msg, type}`）。
 *
 * 判成功用 `type` 而不是 `code`: code 是消息 id 派生的（同一个接口在不同场景下可能是
 * 1053/1054/1056）, 没有稳定的白名单; 而 type 只有四种取值, 语义明确。
 */
export interface RsmResponse<T> {
  code: number
  data: T
  msg: string
  type: 'SUCCESS' | 'WARN' | 'INFO' | 'ERROR'
}

/** 当前登录用户（后端 UserRes） */
export interface CurrentUser {
  id: number
  username: string
  nickname: string
  locked: boolean
}

/** 菜单节点（后端 MenuRes），树形 */
export interface MenuNode {
  id: number
  parentId: number | null
  name: string
  /** 前端路由，如 /ops/work-order */
  path: string | null
  /**
   * 前端页面标识，相对 src/pages/ 的路径（不含扩展名），如 ops/work-order/index。
   * 为空即"目录"，只用于分组、不产生路由。
   */
  component: string | null
  icon: string | null
  sort: number
  /** 在侧边栏隐藏（但仍可路由，用于详情页） */
  hidden: boolean
  keepAlive: boolean
  permissionId: number | null
  /** 所需权限的键；null 表示登录即可见 */
  permissionKey: string | null
  enabled: boolean
  locked: boolean
  source: string
  children: MenuNode[]
}

/** `GET /api/auth/me` 的返回（后端 CurrentUserRes） */
export interface CurrentUserContext {
  user: CurrentUser
  roles: string[]
  /** 权限键列表。持有超级权限时是 ["*"]，判断时必须做通配匹配 */
  permissions: string[]
  menus: MenuNode[]
}

/** 登录返回（sa-token 的 SaTokenInfo，只取前端用得到的字段） */
export interface LoginResult {
  tokenName: string
  tokenValue: string
  isLogin: boolean
  loginId: string
}
