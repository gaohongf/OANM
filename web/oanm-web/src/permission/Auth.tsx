import type { ReactNode } from 'react'

import { useAuthStore } from '../store/auth'

interface AuthProps {
  /** 要求的权限键，如 `DELETE:/api/auth/users/{id}` */
  code: string
  children: ReactNode
  /** 无权限时渲染什么。默认什么都不渲染。 */
  fallback?: ReactNode
}

/**
 * 按钮级权限：无权限时整块不渲染。
 *
 * ## 用法
 * ```tsx
 * <Auth code="POST:/api/auth/roles">
 *   <Button>新建</Button>
 * </Auth>
 * ```
 *
 * ## 它只是体验，不是安全边界
 * 隐藏按钮只让界面干净，**后端仍然会独立校验**（每个接口都有权限键）。
 * 反过来也得注意：如果只靠这个组件而接口没设权限，那就是真的没保护。
 *
 * ## 超管能看见一切
 * 权限列表里是 `["*"]` 时，匹配逻辑会把 `*` 当作通配 —— 这正是后端 sa-token 的语义。
 * 所以这里不需要为超管写特例分支（写特例反而容易和后端不一致）。
 */
export function Auth({ code, children, fallback = null }: AuthProps) {
  const can = useAuthStore((state) => state.can)
  return <>{can(code) ? children : fallback}</>
}
