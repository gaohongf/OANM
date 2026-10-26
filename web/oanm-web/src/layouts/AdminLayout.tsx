import { Suspense, useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import {
  App as AntdApp,
  Avatar,
  Breadcrumb,
  Button,
  Drawer,
  Dropdown,
  Form,
  Grid,
  Input,
  Layout,
  Menu,
  Modal,
  Spin,
  theme,
} from 'antd'
import type { MenuProps } from 'antd'
import {
  EditOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'

import * as authApi from '../api/auth'
import { clearToken } from '../api/http'
import { MessageBell } from '../components/MessageBell'
import { collectPageNodes, type PageNode } from '../router/buildRoutes'
import { useAuthStore } from '../store/auth'
import { resolveIcon } from './menuIcons'
import './adminLayout.css'

interface NicknameForm {
  nickname: string
}

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

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
  const screens = Grid.useBreakpoint()
  // md = 768px。初始渲染时断点信息还是空的，先按桌面处理，避免先闪一个抽屉再切回侧栏。
  const isMobile = screens.md === false
  const [collapsed, setCollapsed] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)

  const menus = useAuthStore((state) => state.menus)
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const setUser = useAuthStore((state) => state.setUser)

  const [nicknameOpen, setNicknameOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [nicknameSubmitting, setNicknameSubmitting] = useState(false)
  const [passwordSubmitting, setPasswordSubmitting] = useState(false)
  const [nicknameForm] = Form.useForm<NicknameForm>()
  const [passwordForm] = Form.useForm<PasswordForm>()

  const { token } = theme.useToken()

  // 菜单树只在 menus 变化时重算。每次渲染都重算会让 antd Menu 的展开状态抖动。
  const pageNodes = useMemo(() => collectPageNodes(menus), [menus])

  // 侧边栏项：hidden 的节点不显示（但仍然有路由，用于详情页这类页面）
  const menuItems = useMemo(() => toMenuItems(pageNodes), [pageNodes])

  const selectedKeys = useMemo(() => [location.pathname], [location.pathname])

  // 当前路由所在的分组。由路径推导而不是写死"运维管理/系统管理"：
  // 以后加新分组不用回来改这里，也不会出现"当前页在折叠的分组里"。
  const routeOpenKeys = useMemo(
    () => toOpenKeys(pageNodes, location.pathname),
    [pageNodes, location.pathname],
  )

  // 展开状态只对"当前这一次停留"有效：切到别的路径就回到按路由推导的默认值 ——
  // 只展开当前页所在的分组；像「概览」这种顶级页面（或不在菜单里的详情页）不展开任何分组。
  //
  // 刻意不按路径长期记住：手动展开某个分组，多半只是为了点进它，这个 key 会被记在
  // **当前页**名下；下次回到这一页时它又被放出来，表现为"点过知识文章再点菜单管理，
  // 知识库一直不收起；点一下角色管理才收起，再点菜单管理又放出来"。
  // 记住 path 是为了区分"手动结果"和"切页后的默认值"，而不是为了跨页复用。
  const [manualOpenKeys, setManualOpenKeys] = useState<{ path: string; keys: string[] } | null>(
    null,
  )
  const openKeys =
    manualOpenKeys?.path === location.pathname ? manualOpenKeys.keys : routeOpenKeys

  const onOpenChange = (keys: string[]) => {
    setManualOpenKeys({ path: location.pathname, keys })
  }

  // 切页后丢弃手动开合。只比较 pathname 是不够的：离开 A 页再回到 A 页时,
  // 那条旧状态还会命中同一个路径（这正是上面注释里那个"来回放出子节点"的现象）。
  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect -- 丢弃上一页的展开状态, 只影响侧栏
    setManualOpenKeys(null)
  }, [location.pathname])

  const breadcrumbItems = useMemo(() => toBreadcrumb(pageNodes, location.pathname), [pageNodes, location.pathname])

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'nickname',
      icon: <EditOutlined />,
      label: '修改昵称',
    },
    {
      key: 'password',
      icon: <KeyOutlined />,
      label: '修改密码',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ]

  const onUserMenuClick: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'nickname') {
      setNicknameOpen(true)
      return
    }
    if (key === 'password') {
      setPasswordOpen(true)
      return
    }
    if (key !== 'logout') {
      return
    }
    await logout()
    message.success('已退出登录')
    navigate('/login', { replace: true })
  }

  const onNicknameFinish = async (values: NicknameForm) => {
    setNicknameSubmitting(true)
    try {
      const nickname = values.nickname.trim()
      await authApi.changeOwnNickname(nickname)
      // 局部更新即可：顶栏直接读 store 里的 user，改完立刻重渲染
      if (user) {
        setUser({ ...user, nickname })
      }
      message.success('昵称已更新')
      setNicknameOpen(false)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '修改昵称失败')
    } finally {
      setNicknameSubmitting(false)
    }
  }

  const onPasswordFinish = async (values: PasswordForm) => {
    setPasswordSubmitting(true)
    try {
      await authApi.changeOwnPassword(values.oldPassword, values.newPassword)
      setPasswordOpen(false)
      message.success('密码已修改，请重新登录')
      // 后端已经注销了全部会话；这里显式清本地状态，不要再用失效的 token 发请求。
      // 不走 logout()：它会再发一个带旧 token 的登出请求，此时必然 401。
      clearToken()
      useAuthStore.getState().handleUnauthorized()
      navigate('/login', { replace: true })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '修改密码失败')
    } finally {
      setPasswordSubmitting(false)
    }
  }

  const brand = (short: boolean) => (
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
      {short ? 'O' : 'OANM'}
    </div>
  )

  const navMenu = (inDrawer: boolean) => (
    <Menu
      theme="dark"
      mode="inline"
      selectedKeys={selectedKeys}
      openKeys={openKeys}
      onOpenChange={onOpenChange}
      items={menuItems}
      onClick={() => {
        if (inDrawer) {
          setDrawerOpen(false)
        }
      }}
    />
  )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider
          collapsible
          collapsed={collapsed}
          trigger={null}
          collapsedWidth={64}
          width={200}
          theme="dark"
        >
          {brand(collapsed)}
          {navMenu(false)}
        </Sider>
      )}

      {/* 手机端不常驻 64px 侧栏：导航进抽屉，内容区拿回整行宽度 */}
      {isMobile && (
        <Drawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          placement="left"
          width={220}
          closable={false}
          styles={{
            header: { display: 'none' },
            body: { padding: 0, background: '#001529' },
          }}
        >
          {brand(false)}
          {navMenu(true)}
        </Drawer>
      )}

      <Layout>
        <Header
          style={{
            padding: isMobile ? '0 12px' : '0 16px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            gap: isMobile ? 8 : 12,
          }}
        >
          <Button
            type="text"
            aria-label={isMobile ? '打开菜单' : collapsed ? '展开菜单' : '收起菜单'}
            icon={
              isMobile || collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />
            }
            onClick={() => {
              if (isMobile) {
                setDrawerOpen(true)
                return
              }
              setCollapsed((value) => !value)
            }}
          />

          <Breadcrumb items={breadcrumbItems} style={{ flex: 1, minWidth: 0 }} />

          {/* 在面包屑与用户菜单之间。放在这里而不是侧边栏：消息是全局的，
              它不属于任何一个菜单分支；而面包屑那一段是 flex:1，把它推到右边刚好 */}
          <MessageBell />

          <Dropdown menu={{ items: userMenuItems, onClick: onUserMenuClick }} placement="bottomRight">
            <Button type="text" aria-label="账号菜单">
              <Avatar
                size="small"
                icon={<UserOutlined />}
                style={isMobile ? undefined : { marginInlineEnd: 8 }}
              />
              {!isMobile && (user?.nickname || user?.username)}
            </Button>
          </Dropdown>
        </Header>

        <Content style={{ margin: isMobile ? 12 : 16 }}>
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

      <Modal
        title="修改昵称"
        open={nicknameOpen}
        okText="保存"
        cancelText="取消"
        confirmLoading={nicknameSubmitting}
        onOk={() => nicknameForm.submit()}
        onCancel={() => setNicknameOpen(false)}
        destroyOnHidden
      >
        <Form<NicknameForm>
          form={nicknameForm}
          layout="vertical"
          preserve={false}
          initialValues={{ nickname: user?.nickname || user?.username || '' }}
          onFinish={(values) => void onNicknameFinish(values)}
        >
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[
              { required: true, whitespace: true, message: '请输入昵称' },
              { max: 64, message: '昵称最多 64 个字符' },
            ]}
          >
            <Input placeholder="显示在右上角的名称" maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="修改密码"
        open={passwordOpen}
        okText="确认修改"
        cancelText="取消"
        confirmLoading={passwordSubmitting}
        onOk={() => passwordForm.submit()}
        onCancel={() => setPasswordOpen(false)}
        destroyOnHidden
      >
        <Form<PasswordForm>
          form={passwordForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => void onPasswordFinish(values)}
        >
          <Form.Item
            name="oldPassword"
            label="当前密码"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, max: 20, message: '密码长度为 6 到 20 个字符' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || value === getFieldValue('newPassword')) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>

          <div style={{ color: 'rgba(0,0,0,0.45)' }}>修改成功后所有设备都需要用新密码重新登录。</div>
        </Form>
      </Modal>
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
      return {
        key: menuKey(node),
        icon: resolveIcon(node.icon),
        label: node.component ? <Link to={node.path}>{node.name}</Link> : node.name,
        children: children.length > 0 ? children : undefined,
      }
    })
}

/**
 * 菜单项 key。
 *
 * 页面节点（有 `component`）用它的路由；目录节点没有路由，用 `dir:<名称>`。
 * **有子节点的页面节点在 antd 里同样渲染成 SubMenu**，key 仍然是它的路由 ——
 * 展开状态的推导必须用同一个 key，否则自动展开会落空（菜单拖拽后目录结构会变，
 * 这类"页面 + 子节点"的节点会真实出现）。
 */
function menuKey(node: PageNode): string {
  return node.component ? node.path : `dir:${node.name}`
}

/** 当前路径 → 它所属的分组 key，用于自动展开侧栏 */
function toOpenKeys(nodes: PageNode[], pathname: string): string[] {
  const walk = (list: PageNode[], ancestors: string[]): string[] | null => {
    for (const node of list) {
      if (node.component && node.path === pathname) {
        return ancestors
      }
      if (node.children.length > 0) {
        const found = walk(node.children, [...ancestors, menuKey(node)])
        if (found) {
          return found
        }
      }
    }
    return null
  }
  return walk(nodes, []) ?? []
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
