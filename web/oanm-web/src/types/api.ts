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

/**
 * 分页结果（后端 `PageRes<T>`）。
 *
 * 后端在 common 里只维护这一份形状（含"每页最多 200 条"这条策略），前端也照它对齐。
 */
export interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

/**
 * 被后端 `@User` 解析过的用户字段。
 *
 * 它是**运行时才变形**的：库里只存 id，序列化时才换成用户对象。
 * 解析成功是 `{id, username}`，解析失败（用户服务不可用）降级成 `{id}` ——
 * 所以两个字段都必须可选。
 *
 * `id` 是字符串而不是数字：用户 id 是雪花 id（19 位），超过 `2^53 - 1` 之后
 * JSON 数字在 JS 里会被取整成**另一个值**，所以后端一律以字符串出网。
 * 这里保留这个类型而不是 `number` 正是为了不让人顺手把它转成数字。
 */
export interface ResolvedUser {
  id: string
  username?: string
  nickname?: string
}

/** 当前登录用户（后端 UserRes） */
export interface CurrentUser {
  id: string
  username: string
  nickname: string
  locked: boolean
}

/** 菜单节点（后端 MenuRes），树形 */
export interface MenuNode {
  id: string
  parentId: string | null
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
  /** 门禁权限 id；空数组表示登录即可见。持有任意一个即可见 */
  permissionIds: string[]
  /**
   * 与 permissionIds 同长度、同顺序的权限键。
   * 某个位置为 null 表示那条权限行已不存在（该关联不授权，但不能因此错位）。
   */
  permissionKeys: (string | null)[]
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
