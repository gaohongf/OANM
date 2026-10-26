import {
  ApiOutlined,
  AppstoreOutlined,
  BellOutlined,
  BookOutlined,
  DashboardOutlined,
  DownloadOutlined,
  FileAddOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'

import type { ReactNode } from 'react'

/**
 * 菜单图标白名单。
 *
 * ## 为什么不做 `import * as Icons from '@ant-design/icons'`
 * 那样会把整套图标（数千个）打进产物里，包体积会因此大出几 MB，
 * 而实际用到的可能只有几个。
 *
 * ## 代价
 * 后端能选的图标名被限制在这张表里。选了表里没有的，会退化成默认图标而不是报错 ——
 * 这是刻意的：图标缺失不该让菜单渲染失败。
 * 新增图标时在这里加一行即可。
 */
const ICONS: Record<string, ReactNode> = {
  dashboard: <DashboardOutlined />,
  setting: <SettingOutlined />,
  file: <FileTextOutlined />,
  'file-done': <FileDoneOutlined />,
  // 「新建文章」菜单用的图标（见 service-auth/db/seed.sql 第 10 段）
  'file-add': <FileAddOutlined />,
  team: <TeamOutlined />,
  user: <UserOutlined />,
  api: <ApiOutlined />,
  appstore: <AppstoreOutlined />,
  robot: <RobotOutlined />,
  // 「消息中心」菜单用的图标（见 service-auth/db/seed.sql 第 8 段）
  bell: <BellOutlined />,
  // 「软件下载」菜单用的图标（见 service-auth/db/seed.sql 第 8 段「自助软件下载」）
  download: <DownloadOutlined />,
  // 「知识库」菜单用的图标（见 service-auth/db/seed.sql 第 10 段）
  book: <BookOutlined />,
}

const DEFAULT_ICON = <AppstoreOutlined />

/** 按名字取图标；名字为空或不在白名单里时给一个默认图标 */
export function resolveIcon(name: string | null): ReactNode {
  if (!name) {
    return DEFAULT_ICON
  }
  return ICONS[name] ?? DEFAULT_ICON
}
