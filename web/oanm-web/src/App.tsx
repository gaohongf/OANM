import { useEffect, useMemo } from 'react'
import { Navigate, useRoutes } from 'react-router'
import type { RouteObject } from 'react-router'
import { Result, Spin } from 'antd'

import { AdminLayout } from './layouts/AdminLayout'
import { buildRoutes, collectPageNodes, firstPagePath } from './router/buildRoutes'
import { useAuthStore } from './store/auth'
import Login from './pages/Login'
import NotFound from './pages/NotFound'

/**
 * 路由出口。
 *
 * ## 为什么用 `useRoutes` 而不是 data router
 * React Router 的 data router（`createBrowserRouter`）**没有 `addRoute`**：
 * 登录之后要动态补挂路由，官方途径是 `patchRoutesOnNavigation`，但它在"首次按路径调用会被缓存"
 * 这一点上有已知的坑；`router.patchRoutes` 又是伪私有 API。
 * <p>
 * `useRoutes` 接收的是**每次渲染重新计算的路由数组** —— 登录拿到菜单后状态一变，
 * 路由自然就在了，不需要任何命令式的注册动作，也就没有"注册时机"这类问题。
 * 代价是没有 data router 的 loader/action，对后台管理界面用不上。
 */
export default function App() {
  const initializing = useAuthStore((state) => state.initializing)
  const user = useAuthStore((state) => state.user)
  const menus = useAuthStore((state) => state.menus)
  const bootstrap = useAuthStore((state) => state.bootstrap)

  useEffect(() => {
    // 挂载时用本地 token 拉一次用户上下文。
    // 不做这一步的话，刷新页面会先渲染登录页再跳回来 —— 因为 store 初始状态是未登录。
    void bootstrap()
  }, [bootstrap])

  const routes = useMemo<RouteObject[]>(() => {
    // 还没判断完登录态时不能建路由：建了会闪一下登录页。
    // 这不是"多此一举的 loading"，而是刷新体验的关键。
    if (initializing) {
      return []
    }

    if (!user) {
      // 未登录：只放登录页，其余一律重定向过去
      return [
        { path: '/login', element: <Login /> },
        { path: '*', element: <Navigate to="/login" replace /> },
      ]
    }

    const pages = collectPageNodes(menus)
    const landing = firstPagePath(pages)

    return [
      // 已登录还去 /login 就直接回首页，免得出现"登录了还停留在登录页"
      { path: '/login', element: <Navigate to="/" replace /> },
      {
        path: '/',
        element: <AdminLayout />,
        children: [
          {
            index: true,
            // 落地页取第一个可见菜单。一个菜单都没有（例如刚建好、还没被授权的用户）时
            // 给一条明确的说明，而不是硬塞一个业务页面 —— 那种情况下任何业务页面都可能
            // 因为缺权限而报错，反而让人以为是系统坏了。
            element: landing ? <Navigate to={landing} replace /> : <NoMenu />,
          },
          ...buildRoutes(pages),
          // 放在最后：它只在前面都没匹配上时生效，所以会渲染在布局内部（保留侧边栏）
          { path: '*', element: <NotFound /> },
        ],
      },
    ]
  }, [initializing, user, menus])

  if (initializing) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" tip="正在加载..." />
      </div>
    )
  }

  return <RouteTree routes={routes} />
}

/**
 * 把 useRoutes 单独包一层组件。
 *
 * 原因：`useRoutes` 是 hook，必须在每次渲染时都调用。如果写在 App 里，上面那个
 * `initializing` 的提前 return（返回 Spin）会让 hook 的调用次数在两次渲染间变化，
 * 违反 hooks 规则。拆出来之后 hook 的调用点不再受条件 return 影响。
 */
function RouteTree({ routes }: { routes: RouteObject[] }) {
  return useRoutes(routes)
}

/**
 * 登录成功但一个可见菜单都没有时的落地页。
 *
 * 这不是异常情况：新建的用户在没被授权之前就是这样。所以文案要指路
 * （"找管理员授权"），而不是报错 —— 报错会让用户以为是登录失败。
 */
function NoMenu() {
  return (
    <Result
      status="info"
      title="还没有可访问的功能"
      subTitle="你的账号目前没有任何已授权的菜单。请联系管理员为你的角色授权，或让管理员直接给你分配角色。"
    />
  )
}
