import { useAuthStore } from '../store/auth'

/**
 * 只需要判断、不需要包裹组件时的 hook 形式：
 * ```tsx
 * const canDelete = usePermission('DELETE:/api/auth/roles/{roleName}')
 * ```
 *
 * 单独一个文件而不是和 `<Auth>` 放一起：那个文件只导出组件，才能让 React Fast Refresh
 * 正常工作（同时导出组件和普通函数时，HMR 会退化成整页刷新）。
 */
export function usePermission(code: string): boolean {
  return useAuthStore((state) => state.can(code))
}
