import { lazy } from 'react'
import type { ComponentType, LazyExoticComponent } from 'react'

/**
 * 页面组件白名单。
 *
 * ## 为什么要有这一层
 * 后端的 `menus.component` 是一个字符串。如果直接按它去 `import(...)`，那后端就拥有了
 * "让前端加载任意模块"的能力 —— 一旦菜单数据被改坏或被越权写入，就可能加载到不该加载的
 * 模块。有了白名单，后端只能**选一个已存在的页面**，选错了就渲染 404 并告警。
 *
 * ## `component` 的取值约定（跨阶段契约）
 * 相对 `src/pages/` 的路径，**不含扩展名**，例如：
 * - `ops/work-order/index`  → `src/pages/ops/work-order/index.tsx`
 * - `Dashboard`             → `src/pages/Dashboard.tsx`
 *
 * 选这个形式是因为 `import.meta.glob` 的键就是文件路径，剥掉前缀和扩展名即可直接索引，
 * **不需要额外维护映射表**；库里存的值也一眼能对应到磁盘上的文件。
 */
/**
 * 抓取 pages 下所有页面，但排除两类不是"页面"的东西。
 *
 * ## 1. 框架页面
 * `Login` 与 `NotFound` 不是业务页面：前者在未登录时就要能渲染，后者是路由兜底。
 * 放进白名单意味着菜单可以指向它们 —— 那会得到一个能点开、但语义完全不对的页面
 * （例如一个菜单点开是登录页）。排除掉比"约定别这么配"更可靠。
 * 顺带也让它们在构建时被拆成独立 chunk，而不是因为既被静态导入又被动态导入而滞留在主包
 * （Vite 会为此报 INEFFECTIVE_DYNAMIC_IMPORT）。
 *
 * ## 2. `components/` 子目录里的东西
 * 页面私有的子组件按约定放在同级的 `components/` 下（如
 * `pages/system/components/RolePermissionDrawer.tsx`）。它们<b>不是页面</b>：
 * 混进白名单意味着菜单可以指向一个抽屉组件，然后整页渲染出一个孤零零的抽屉。
 * 用目录名做约定比逐个列名单可靠 —— 新加子组件时不需要记得改这里。
 */
const pageModules = import.meta.glob([
  '../pages/**/*.tsx',
  '!../pages/Login.tsx',
  '!../pages/NotFound.tsx',
  '!../pages/**/components/**',
])

const PAGES_PREFIX = '../pages/'

/** component 值 → 懒加载函数 */
const registry = new Map<string, () => Promise<unknown>>()

for (const [modulePath, loader] of Object.entries(pageModules)) {
  if (!modulePath.startsWith(PAGES_PREFIX)) {
    continue
  }
  const key = modulePath.slice(PAGES_PREFIX.length).replace(/\.tsx$/, '')
  registry.set(key, loader)
}

/** 白名单里全部可用的 component 值。用于排错（例如在控制台里列出来和库里存的比对）。 */
export const availableComponents: string[] = [...registry.keys()].sort()

/**
 * 按 component 值取懒加载函数。
 *
 * @returns 白名单里没有这个值时为 undefined —— 调用方必须处理，不能当成"没配组件"
 */
export function resolvePage(component: string): (() => Promise<unknown>) | undefined {
  return registry.get(component)
}

/**
 * 懒加载组件的缓存。
 *
 * 必须缓存，不能每次渲染都 `lazy(loader)`：`lazy()` 每次返回的都是**新的**组件类型，
 * React 会把它当成另一个组件而卸载重建 —— 表现为每次父组件重渲染，页面就重新挂载一次
 * （表单内容丢失、请求重发）。
 */
const lazyPages = new Map<string, LazyExoticComponent<ComponentType>>()

/**
 * 按 component 值取懒加载组件。
 *
 * @returns 白名单里没有这个值时为 undefined
 */
export function getLazyPage(component: string): LazyExoticComponent<ComponentType> | undefined {
  const cached = lazyPages.get(component)
  if (cached) {
    return cached
  }

  const loader = registry.get(component)
  if (!loader) {
    return undefined
  }

  const created = lazy(loader as () => Promise<{ default: ComponentType }>)
  lazyPages.set(component, created)
  return created
}

/** 便于类型标注：页面模块的默认导出应当是一个组件 */
export type PageModule = { default: ComponentType }
