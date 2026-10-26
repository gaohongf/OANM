import { request } from './http'
import type { PageResult } from '../types/api'

/**
 * 运维知识库：文章、分类、修订历史与语义检索。
 *
 * ## 枚举用字符串联合类型
 * `tsconfig.app.json` 开了 `erasableSyntaxOnly`，TS 的 `enum` 会生成运行时代码，
 * 编译不过；后端也是按枚举 `name()` 存库和出网，两边用同一组字面量最省事。
 *
 * ## 所有 id 都是 string
 * 它们是雪花 id，转成 number 会被 JS 取整成另一个值。
 */

export type ArticleStatus = 'DRAFT' | 'PUBLISHED'

export type IndexStatus = 'NONE' | 'INDEXING' | 'READY' | 'FAILED'

/** 本次搜索实际使用的模式；KEYWORD 表示语义检索不可用、已降级 */
export type SearchMode = 'SEMANTIC' | 'KEYWORD'

export type RevisionChangeType = 'CREATE' | 'UPDATE' | 'ROLLBACK'

/** 权限键。与后端 `permissions.permission_key` 逐字一致，用于 `<Auth code>`。 */
export const KNOWLEDGE_PERMISSIONS = {
  /** 管理页列表（菜单挂的就是它） */
  manageList: 'GET:/api/knowledge/manage/articles',
  /** 管理页详情（编辑表单回填正文） */
  manageDetail: 'GET:/api/knowledge/manage/articles/{id}',
  create: 'POST:/api/knowledge/articles',
  update: 'PUT:/api/knowledge/articles/{id}',
  changeStatus: 'PUT:/api/knowledge/articles/{id}/status',
  remove: 'DELETE:/api/knowledge/articles/{id}',
  reindex: 'POST:/api/knowledge/articles/{id}/reindex',
  revisions: 'GET:/api/knowledge/manage/articles/{id}/revisions',
  revisionDetail: 'GET:/api/knowledge/manage/articles/{id}/revisions/{revisionId}',
  restore: 'POST:/api/knowledge/articles/{id}/revisions/{revisionId}/restore',
  categoryCreate: 'POST:/api/knowledge/categories',
  categoryUpdate: 'PUT:/api/knowledge/categories/{id}',
  categoryRemove: 'DELETE:/api/knowledge/categories/{id}',
} as const

// ---- 响应类型 ----

export interface KnowledgeCategory {
  id: string
  name: string
  sort: number
  /** 该分类下已发布文章数；管理端分类维护不使用 */
  publishedCount: number
}

export interface KnowledgeRow {
  id: string
  title: string
  summary: string | null
  categoryId: string
  categoryName: string | null
  publishTime: string | null
  updateTime: string | null
  viewCount: number
}

export interface KnowledgeDetail extends KnowledgeRow {
  /** Markdown 正文 */
  content: string
}

export interface KnowledgeManageRow {
  id: string
  title: string
  summary: string | null
  categoryId: string
  categoryName: string | null
  status: ArticleStatus
  indexStatus: IndexStatus
  /** 最近一次索引失败原因；成功时为 null */
  indexError: string | null
  indexedAt: string | null
  publishTime: string | null
  updateTime: string | null
  viewCount: number
}

export interface KnowledgeManageDetail extends KnowledgeManageRow {
  content: string
}

/** 修订列表行：不含正文（列表接口只返回摘要字段），预览走详情接口 */
export interface KnowledgeRevision {
  id: string
  articleId: string
  revisionNo: number
  changeType: RevisionChangeType
  sourceRevisionId: string | null
  title: string
  summary: string | null
  content: string | null
  categoryId: string
  createTime: string | null
}

export interface KnowledgeRevisionDetail extends KnowledgeRevision {
  content: string
}

export interface KnowledgeSearchItem extends KnowledgeRow {
  /** 语义检索命中才有；关键字补充进来的行为 null */
  score: number | null
}

export interface KnowledgeSearchResult {
  mode: SearchMode
  items: KnowledgeSearchItem[]
}

// ---- 请求载荷 ----

export interface ListKnowledgeParams {
  current: number
  size: number
  categoryId?: string
  /** 不带关键字时是普通分页浏览 */
  keyword?: string
}

export interface SearchKnowledgeParams {
  q: string
  limit?: number
  categoryId?: string
}

export interface ListManageKnowledgeParams {
  current: number
  size: number
  categoryId?: string
  status?: ArticleStatus
  keyword?: string
}

export interface SaveKnowledgeArticlePayload {
  title: string
  summary?: string
  /** Markdown 正文 */
  content: string
  categoryId: string
  /** 省略表示新建用 DRAFT、编辑时保持原状态 */
  status?: ArticleStatus
}

export interface SaveKnowledgeCategoryPayload {
  name: string
  sort: number
}

// ---- 浏览页接口（登录即可） ----

export function listKnowledgeCategories(): Promise<KnowledgeCategory[]> {
  return request<KnowledgeCategory[]>('/api/knowledge/categories')
}

export function listKnowledgeArticles(
  params: ListKnowledgeParams,
): Promise<PageResult<KnowledgeRow>> {
  return request<PageResult<KnowledgeRow>>('/api/knowledge/articles', {
    query: {
      current: params.current,
      size: params.size,
      categoryId: params.categoryId,
      keyword: params.keyword,
    },
  })
}

/** 语义优先、关键字补足；返回的 mode 说明是否发生了降级 */
export function searchKnowledge(params: SearchKnowledgeParams): Promise<KnowledgeSearchResult> {
  return request<KnowledgeSearchResult>('/api/knowledge/articles/search', {
    query: {
      q: params.q,
      limit: params.limit,
      categoryId: params.categoryId,
    },
  })
}

export function getKnowledgeArticle(id: string): Promise<KnowledgeDetail> {
  return request<KnowledgeDetail>(`/api/knowledge/articles/${id}`)
}

// ---- 管理接口 ----

export function listManageKnowledge(
  params: ListManageKnowledgeParams,
): Promise<PageResult<KnowledgeManageRow>> {
  return request<PageResult<KnowledgeManageRow>>('/api/knowledge/manage/articles', {
    query: {
      current: params.current,
      size: params.size,
      categoryId: params.categoryId,
      status: params.status,
      keyword: params.keyword,
    },
  })
}

export function getManageKnowledge(id: string): Promise<KnowledgeManageDetail> {
  return request<KnowledgeManageDetail>(`/api/knowledge/manage/articles/${id}`)
}

export function createKnowledgeArticle(
  payload: SaveKnowledgeArticlePayload,
): Promise<{ id: string }> {
  return request<{ id: string }>('/api/knowledge/articles', { method: 'POST', body: payload })
}

export function updateKnowledgeArticle(
  id: string,
  payload: SaveKnowledgeArticlePayload,
): Promise<void> {
  return request<void>(`/api/knowledge/articles/${id}`, { method: 'PUT', body: payload })
}

export function changeKnowledgeArticleStatus(
  id: string,
  status: ArticleStatus,
): Promise<void> {
  return request<void>(`/api/knowledge/articles/${id}/status`, {
    method: 'PUT',
    body: { status },
  })
}

export function deleteKnowledgeArticle(id: string): Promise<void> {
  return request<void>(`/api/knowledge/articles/${id}`, { method: 'DELETE' })
}

/** 异步重建索引：接口只表示已受理，结果看列表里的索引状态 */
export function reindexKnowledgeArticle(id: string): Promise<void> {
  return request<void>(`/api/knowledge/articles/${id}/reindex`, { method: 'POST' })
}

export function listKnowledgeRevisions(articleId: string): Promise<KnowledgeRevision[]> {
  return request<KnowledgeRevision[]>(`/api/knowledge/manage/articles/${articleId}/revisions`)
}

export function getKnowledgeRevision(
  articleId: string,
  revisionId: string,
): Promise<KnowledgeRevisionDetail> {
  return request<KnowledgeRevisionDetail>(
    `/api/knowledge/manage/articles/${articleId}/revisions/${revisionId}`,
  )
}

export function restoreKnowledgeRevision(
  articleId: string,
  revisionId: string,
): Promise<void> {
  return request<void>(
    `/api/knowledge/articles/${articleId}/revisions/${revisionId}/restore`,
    { method: 'POST' },
  )
}

export function createKnowledgeCategory(
  payload: SaveKnowledgeCategoryPayload,
): Promise<{ id: string }> {
  return request<{ id: string }>('/api/knowledge/categories', { method: 'POST', body: payload })
}

export function updateKnowledgeCategory(
  id: string,
  payload: SaveKnowledgeCategoryPayload,
): Promise<void> {
  return request<void>(`/api/knowledge/categories/${id}`, { method: 'PUT', body: payload })
}

export function deleteKnowledgeCategory(id: string): Promise<void> {
  return request<void>(`/api/knowledge/categories/${id}`, { method: 'DELETE' })
}
