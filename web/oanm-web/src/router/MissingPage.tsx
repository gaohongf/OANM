import { Result } from 'antd'

/**
 * 菜单指向的 component 不在白名单里时的占位。
 *
 * 比白屏好：至少用户知道这是配置问题而不是系统坏了，也知道该去找谁。
 * <p>
 * 单独一个文件是因为 `buildRoutes.tsx` 只导出函数 —— 组件和函数混在一个文件里会让
 * React Fast Refresh 退化成整页刷新。
 */
export function MissingPage({ name, component }: { name: string; component: string }) {
  return (
    <Result
      status="warning"
      title="页面未注册"
      subTitle={
        <>
          菜单「{name}」指向的前端页面 <code>{component}</code> 不在白名单里。
          请让后端把该菜单的 component 改成 src/pages/ 下确实存在的页面路径。
        </>
      }
    />
  )
}
