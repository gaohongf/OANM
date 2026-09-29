import type { RouteObject } from 'react-router'

import { PageLoader } from './PageLoader'
import { getLazyPage } from './registry'
import type { MenuNode } from '../types/api'

/**
 * 菜单树 → 路由表。
 *
 * ## 为什么是"拍平"而不是嵌套
 * 目录节点（没有 `component`）不产生路由，只用来分组；页面节点带着自己的完整路径
 * （如 `/ops/work-order`）直接挂在布局路由下。
 * 嵌套路由需要每个中间层页面里有 `<Outlet/>`，而这些页面都是叶子页面 ——
 * 强行嵌套只会逼着每个目录都造一个纯转发的空组件。
 */

/** 拍平后的页面节点 */
export interface PageNode {
  /** 完整路径，如 /ops/work-order */
  path: string
  name: string
  component: string
  icon: string | null
  /** 是否是叶子节点（没有可见子菜单） */
  children: PageNode[]
  hidden: boolean
}

/**
 * 从菜单树里抽出所有"有页面"的节点，并保留层级（供侧边栏渲染）。
 *
 * 后端已经按权限过滤过一遍，所以这里不需要再判权限。
 */
export function collectPageNodes(menus: MenuNode[]): PageNode[] {
  return menus
    .filter((node) => node.enabled)
    .map((node) => ({
      path: node.path ?? '',
      name: node.name,
      component: node.component ?? '',
      icon: node.icon,
      hidden: node.hidden,
      children: collectPageNodes(node.children),
    }))
    .filter((node) => {
      // 目录（没有 component）是合法的中间层，但要至少有一个可见子节点才有意义；
      // 叶子节点必须有 component 与 path，否则它指不到任何页面。
      if (node.component) {
        if (!node.path) {
          // 后端有交叉校验（component 必须配 path），走到这里说明数据绕过了校验。
          // 报出来而不是静默跳过 —— 否则用户只会看到"菜单点不动"。
          console.warn(`[router] 菜单「${node.name}」配置了 component 但没有 path，已跳过`)
          return false
        }
        return true
      }
      return node.children.length > 0
    })
}

/** 第一个可跳转的页面路径，用作登录后的默认落地页 */
export function firstPagePath(nodes: PageNode[]): string | null {
  for (const node of nodes) {
    // 优先用自己（目录通常没有路径），否则递归找子节点
    if (node.component && node.path) {
      return node.path
    }
    const child = firstPagePath(node.children)
    if (child) {
      return child
    }
  }
  return null
}

/**
 * 生成路由。
 *
 * 白名单里查不到的 component 会被替换成一个"页面不存在"的占位组件，而不是让
 * 整个路由表构建失败 —— 一个菜单配错不该导致整个后台打不开。
 */
export function buildRoutes(nodes: PageNode[]): RouteObject[] {
  const routes: RouteObject[] = []

  const walk = (list: PageNode[]) => {
    for (const node of list) {
      if (node.component && node.path) {
        routes.push({
          // 子路由用相对路径（去掉开头的 /）
          path: node.path.replace(/^\/+/, ''),
          // 组件在这里解析一次并传下去。查不到时传 undefined，由 PageLoader 给说明页 ——
          // 解析失败不该让整张路由表构建失败，一个菜单配错不该让整个后台打不开。
          element: (
            <PageLoader
              name={node.name}
              component={node.component}
              page={getLazyPage(node.component)}
            />
          ),
        })
      }
      walk(node.children)
    }
  }

  walk(nodes)
  return routes
}

