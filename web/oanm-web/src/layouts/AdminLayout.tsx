import { Suspense, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { App as AntdApp, Avatar, Breadcrumb, Button, Dropdown, Layout, Menu, Spin, theme } from 'antd'
import type { MenuProps } from 'antd'
import { LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, UserOutlined } from '@ant-design/icons'

import { collectPageNodes, type PageNode } from '../router/buildRoutes'
import { useAuthStore } from '../store/auth'
import { resolveIcon } from './menuIcons'

const { Header, Sider, Content } = Layout

/**
 * 后台主框架：侧边栏菜单 + 顶栏 + 内容区。
 *
 * 菜单由后端返回的菜单树渲染（已经按权限过滤过），前端不再单独判一次权限 ——
 * 两处各判一次容易出现不一致，而"菜单显示了但打不开"比"菜单不显示"更让人困惑。
 */
export function AdminLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [collapsed, setCollapsed] = useState(false)

  const menus = useAuthStore((state) => state.menus)
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  const { token } = theme.useToken()

  // 菜单树只在 menus 变化时重算。每次渲染都重算会让 antd Menu 的展开状态抖动。
  const pageNodes = useMemo(() => collectPageNodes(menus), [menus])

  // 侧边栏项：hidden 的节点不显示（但仍然有路由，用于详情页这类页面）
  const menuItems = useMemo(() => toMenuItems(pageNodes), [pageNodes])

  const selectedKeys = useMemo(() => [location.pathname], [location.pathname])

  const breadcrumbItems = useMemo(() => toBreadcrumb(pageNodes, location.pathname), [pageNodes, location.pathname])

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ]

  const onUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key !== 'logout') {
      return
    }
    await logout()
    message.success('已退出登录')
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        // 手机端：窄屏时自动收起。standard.md 要求适配手机端。
        breakpoint="lg"
        collapsedWidth={64}
        theme="dark"
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 600,
            letterSpacing: 1,
          }}
        >
          {collapsed ? 'O' : 'OANM'}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={selectedKeys} items={menuItems} />
      </Sider>

      <Layout>
        <Header
          style={{
            padding: '0 16px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <Button
            type="text"
            aria-label={collapsed ? '展开菜单' : '收起菜单'}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((value) => !value)}
          />

          <Breadcrumb items={breadcrumbItems} style={{ flex: 1 }} />

          <Dropdown menu={{ items: userMenuItems, onClick: onUserMenuClick }} placement="bottomRight">
            <Button type="text">
              <Avatar size="small" icon={<UserOutlined />} style={{ marginInlineEnd: 8 }} />
              {user?.nickname || user?.username}
            </Button>
          </Dropdown>
        </Header>

        <Content style={{ margin: 16 }}>
          {/* key 用 pathname: 切换页面时重放淡入动画, 避免内容"啪"地换掉 */}
          <div key={location.pathname} className="page-transition">
            <Suspense
              fallback={
                <div style={{ padding: 48, textAlign: 'center' }}>
                  <Spin />
                </div>
              }
            >
              <Outlet />
            </Suspense>
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}

/**
 * 页面节点树 → antd Menu 的 items。
 *
 * 返回类型写成 `NonNullable<...>` 而不是 `MenuProps['items']`：后者含 undefined，
 * 而本函数总是返回数组 —— 让调用方少一次判空，也避免 `children: undefined` 这类
 * "看起来能省"的写法。
 */
function toMenuItems(nodes: PageNode[]): NonNullable<MenuProps['items']> {
  return nodes
    .filter((node) => !node.hidden)
    .map((node) => {
      const children = toMenuItems(node.children)
      // 目录节点（没有页面）用 key 但不可点击 —— 它的路径是空的
      const key = node.component ? node.path : `dir:${node.name}`
      return {
        key,
        icon: resolveIcon(node.icon),
        label: node.component ? <Link to={node.path}>{node.name}</Link> : node.name,
        children: children.length > 0 ? children : undefined,
      }
    })
}

/** 当前路径 → 面包屑（按菜单层级拼出来，而不是只显示当前页名） */
function toBreadcrumb(nodes: PageNode[], pathname: string): { title: string }[] {
  const trail: string[] = []

  const walk = (list: PageNode[], ancestors: string[]): boolean => {
    for (const node of list) {
      const path = [...ancestors, node.name]
      if (node.path && node.path === pathname) {
        trail.push(...path)
        return true
      }
      if (walk(node.children, path)) {
        return true
      }
    }
    return false
  }

  walk(nodes, [])
  // 匹配不到就只显示"首页" —— 例如手敲了一个不在菜单里的路径（详情页）
  return trail.length > 0 ? trail.map((title) => ({ title })) : [{ title: '首页' }]
}
