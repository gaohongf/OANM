import { request } from './http'
import type { PageResult } from '../types/api'

/**
 * 自助软件下载中心：分类、软件、版本与下载。
 *
 * ## 平台枚举用字符串联合类型
 * `tsconfig.app.json` 开了 `erasableSyntaxOnly`，TS 的 `enum` 会生成运行时代码，
 * 编译不过；后端也是按枚举 `name()` 存库和出网，两边用同一组字面量最省事。
 *
 * ## 所有 id 都是 string
 * 它们是雪花 id，转成 number 会被 JS 取整成另一个值。
 */

export type SoftwarePlatform = 'WINDOWS' | 'MACOS' | 'LINUX' | 'COMMON'

export interface EnumOption<T extends string> {
  value: T
  label: string
  /** antd Tag / 图例的颜色，页面里不要另写一套 */
  color: string
}

export const PLATFORM_OPTIONS: readonly EnumOption<SoftwarePlatform>[] = [
  { value: 'WINDOWS', label: 'Windows', color: 'blue' },
  { value: 'MACOS', label: 'macOS', color: 'purple' },
  { value: 'LINUX', label: 'Linux', color: 'orange' },
  { value: 'COMMON', label: '通用', color: 'green' },
]

/** 按值取平台元数据；未知值返回 undefined，由调用方原样显示 */
export function findPlatform(value: string | null | undefined): EnumOption<SoftwarePlatform> | undefined {
  if (!value) {
    return undefined
  }
  return PLATFORM_OPTIONS.find((option) => option.value === value)
}

/** 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。 */
export const SOFTWARE_PERMISSIONS = {
  /** 管理页列表（菜单挂的就是它） */
  manageList: 'GET:/api/software/manage',
  /** 管理页详情 / 版本抽屉 */
  manageDetail: 'GET:/api/software/manage/{id}',
  create: 'POST:/api/software',
  update: 'PUT:/api/software/{id}',
  toggle: 'PUT:/api/software/{id}/enabled',
  remove: 'DELETE:/api/software/{id}',
  versionCreate: 'POST:/api/software/{id}/versions',
  versionUpdate: 'PUT:/api/software/versions/{versionId}',
  versionToggle: 'PUT:/api/software/versions/{versionId}/enabled',
  versionRemove: 'DELETE:/api/software/versions/{versionId}',
  categoryCreate: 'POST:/api/software/categories',
  categoryUpdate: 'PUT:/api/software/categories/{id}',
  categoryRemove: 'DELETE:/api/software/categories/{id}',
} as const

// ---- 响应类型 ----

export interface SoftwareCategory {
  id: string
  name: string
  sort: number
}

export interface SoftwareRow {
  id: string
  name: string
  vendor: string | null
  categoryId: string
  categoryName: string | null
  /** 最新启用版本的 id；为空表示一个可下载版本都没有 */
  latestVersionId: string | null
  latestVersionNo: string | null
  /** 启用版本覆盖的平台 */
  platforms: SoftwarePlatform[]
  /** 启用版本数 */
  versionCount: number
  /** 全部未删除版本的下载次数之和 */
  downloadCount: number
  updateTime: string | null
}

export interface SoftwareVersionRow {
  id: string
  versionNo: string
  platform: SoftwarePlatform
  releaseNote: string | null
  fileName: string | null
  fileSize: number | null
  downloadCount: number | null
  createTime: string | null
}

export interface SoftwareDetail extends SoftwareRow {
  description: string | null
  /** 只含启用中的版本，按创建时间倒序 */
  versions: SoftwareVersionRow[]
}

export interface SoftwareManageRow {
  id: string
  name: string
  vendor: string | null
  categoryId: string
  categoryName: string | null
  /** 简介。列表里也带，编辑表单用它回填 */
  description: string | null
  /** 最新未删除版本号（无论是否启用） */
  latestVersionNo: string | null
  platforms: SoftwarePlatform[]
  /** 全部未删除版本数 */
  versionCount: number
  /** 其中启用中的版本数 */
  enabledVersionCount: number
  downloadCount: number
  enabled: boolean
  updateTime: string | null
}

export interface SoftwareManageVersion extends SoftwareVersionRow {
  fileId: string | null
  fileUrl: string | null
  enabled: boolean
}

export interface SoftwareManageDetail extends SoftwareManageRow {
  /** 全部未删除版本（含停用） */
  versions: SoftwareManageVersion[]
}

export interface DownloadResult {
  /** 同源相对地址，形如 /file/2026/10/24/xxx.exe */
  url: string
  fileName: string
}

// ---- 请求载荷 ----

export interface ListSoftwareParams {
  current: number
  size: number
  /** 同时匹配软件名与厂商 */
  keyword?: string
  categoryId?: string
  platform?: SoftwarePlatform
}

export interface ListManageSoftwareParams extends ListSoftwareParams {
  enabled?: boolean
}

export interface SaveSoftwarePayload {
  name: string
  vendor?: string
  categoryId: string
  description?: string
  /** 编辑时省略表示保持原值 */
  enabled?: boolean
}

/**
 * 版本载荷。
 *
 * 文件字段是一整组：编辑时不重新上传就**一个都别传**，后端会保留原文件；
 * 传了一个就必须传全，否则后端拒绝。这里用可选字段表达，由表单保证成组出现。
 */
export interface SaveSoftwareVersionPayload {
  versionNo: string
  platform: SoftwarePlatform
  releaseNote?: string
  fileId?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  enabled?: boolean
}

export interface SaveSoftwareCategoryPayload {
  name: string
  sort: number
}

// ---- 下载页接口（登录即可） ----

export function listSoftware(params: ListSoftwareParams): Promise<PageResult<SoftwareRow>> {
  return request<PageResult<SoftwareRow>>('/api/software', {
    query: {
      current: params.current,
      size: params.size,
      keyword: params.keyword,
      categoryId: params.categoryId,
      platform: params.platform,
    },
  })
}

export function getSoftwareDetail(id: string): Promise<SoftwareDetail> {
  return request<SoftwareDetail>(`/api/software/${id}`)
}

export function listSoftwareCategories(): Promise<SoftwareCategory[]> {
  return request<SoftwareCategory[]>('/api/software/categories')
}

/** 计数并取回下载地址。地址由后端校验过必须以 `/file/` 开头。 */
export function downloadSoftwareVersion(versionId: string): Promise<DownloadResult> {
  return request<DownloadResult>(`/api/software/versions/${versionId}/download`, { method: 'POST' })
}

// ---- 管理接口 ----

export function listManageSoftware(
  params: ListManageSoftwareParams,
): Promise<PageResult<SoftwareManageRow>> {
  return request<PageResult<SoftwareManageRow>>('/api/software/manage', {
    query: {
      current: params.current,
      size: params.size,
      keyword: params.keyword,
      categoryId: params.categoryId,
      platform: params.platform,
      // http.ts 的 query 只收 string/number；Spring 能把 "true"/"false" 绑成 Boolean
      enabled: params.enabled === undefined ? undefined : String(params.enabled),
    },
  })
}

export function getManageSoftware(id: string): Promise<SoftwareManageDetail> {
  return request<SoftwareManageDetail>(`/api/software/manage/${id}`)
}

export function createSoftware(payload: SaveSoftwarePayload): Promise<{ id: string }> {
  return request<{ id: string }>('/api/software', { method: 'POST', body: payload })
}

export function updateSoftware(id: string, payload: SaveSoftwarePayload): Promise<void> {
  return request<void>(`/api/software/${id}`, { method: 'PUT', body: payload })
}

export function setSoftwareEnabled(id: string, enabled: boolean): Promise<void> {
  return request<void>(`/api/software/${id}/enabled`, { method: 'PUT', body: { enabled } })
}

export function deleteSoftware(id: string): Promise<void> {
  return request<void>(`/api/software/${id}`, { method: 'DELETE' })
}

export function createSoftwareVersion(
  softwareId: string,
  payload: SaveSoftwareVersionPayload,
): Promise<{ id: string }> {
  return request<{ id: string }>(`/api/software/${softwareId}/versions`, {
    method: 'POST',
    body: payload,
  })
}

export function updateSoftwareVersion(
  versionId: string,
  payload: SaveSoftwareVersionPayload,
): Promise<void> {
  return request<void>(`/api/software/versions/${versionId}`, { method: 'PUT', body: payload })
}

export function setSoftwareVersionEnabled(versionId: string, enabled: boolean): Promise<void> {
  return request<void>(`/api/software/versions/${versionId}/enabled`, {
    method: 'PUT',
    body: { enabled },
  })
}

export function deleteSoftwareVersion(versionId: string): Promise<void> {
  return request<void>(`/api/software/versions/${versionId}`, { method: 'DELETE' })
}

export function createSoftwareCategory(
  payload: SaveSoftwareCategoryPayload,
): Promise<{ id: string }> {
  return request<{ id: string }>('/api/software/categories', { method: 'POST', body: payload })
}

export function updateSoftwareCategory(
  id: string,
  payload: SaveSoftwareCategoryPayload,
): Promise<void> {
  return request<void>(`/api/software/categories/${id}`, { method: 'PUT', body: payload })
}

export function deleteSoftwareCategory(id: string): Promise<void> {
  return request<void>(`/api/software/categories/${id}`, { method: 'DELETE' })
}
