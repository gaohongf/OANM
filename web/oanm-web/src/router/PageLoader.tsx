import type { ComponentType, LazyExoticComponent } from 'react'

import { MissingPage } from './MissingPage'
import { availableComponents } from './registry'

interface PageLoaderProps {
  /** 菜单名，仅用于告警文案 */
  name: string
  /** 菜单里配的 component 值，仅用于告警文案 */
  component: string
  /**
   * 已经解析好的页面组件。
   * <p>
   * 刻意<b>由调用方解析并传进来</b>，而不是在这里按名字去查：在 render 里查表拿组件，
   * React 的 lint 规则无法判断它是否稳定，会判定为"每次渲染都在创建新组件"（状态会被清空）。
   * 由 `buildRoutes` 在建路由表时解析一次，就既避开了这个歧义，也让"解析"与"渲染"各归其位。
   */
  page?: LazyExoticComponent<ComponentType>
}

/**
 * 渲染菜单指向的页面；component 值不在白名单里时给一个说明页。
 */
export function PageLoader({ name, component, page: Page }: PageLoaderProps) {
  if (!Page) {
    // 库里的 component 与前端文件对不上。这是配置问题而不是运行时故障，
    // 所以渲染说明页并告警，而不是抛错 —— 一个菜单配错不该让整个后台打不开。
    console.warn(
      `[router] 菜单「${name}」指向的页面 "${component}" 不在白名单里。` +
        `可用的值：${availableComponents.join(', ')}`,
    )
    return <MissingPage name={name} component={component} />
  }

  return <Page />
}
