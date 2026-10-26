import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import type { CSSProperties } from 'react'
import { Alert, App as AntdApp, Button, Input, Spin } from 'antd'
import { EyeOutlined, SearchOutlined } from '@ant-design/icons'

import { listKnowledgeArticles, listKnowledgeCategories, searchKnowledge } from '../../api/knowledge'
import type {
  KnowledgeCategory,
  KnowledgeRow,
  KnowledgeSearchItem,
  SearchMode,
} from '../../api/knowledge'
import { formatDateTime } from '../../api/workOrder'
import { KnowledgeDetailDrawer } from './components/KnowledgeDetailDrawer'
import './knowledgeShelf.css'

/** 搜索不分页，直接取前 N 篇；后端还会再按 oanm.knowledge.search-limit 截断 */
const SEARCH_LIMIT = 20
/** 每次取 8 本：桌面 4 列 × 2 排；滚到书架底部再追加下一页 */
const BROWSE_PAGE_SIZE = 8
/** 输入停顿后再查；分类是点击操作，立即生效 */
const SEARCH_DEBOUNCE_MS = 320
/** 发布时间在这个天数内算新书 */
const NEW_WINDOW_DAYS = 7
/** NEW 只留给最新的一两本；全架都挂 NEW 等于没挂 */
const NEW_BADGE_LIMIT = 2

const DESKTOP_QUERY = '(min-width: 992px)'
const TABLET_QUERY = '(min-width: 576px)'

/**
 * 书架列数。
 *
 * 用 matchMedia 而不是 antd 的 Grid.useBreakpoint：这里只需要"视口够不够宽"这一个
 * 布尔量，自己订阅比引入断点上下文更直接，也避免两套断点阈值不一致。
 */
function subscribeShelfColumns(onChange: () => void) {
  // 必须留住这两个对象：matchMedia 每次调用都可能返回新实例，
  // 在清理函数里重新 matchMedia 再 removeEventListener 是摘不掉监听器的。
  const desktop = window.matchMedia(DESKTOP_QUERY)
  const tablet = window.matchMedia(TABLET_QUERY)
  desktop.addEventListener('change', onChange)
  tablet.addEventListener('change', onChange)
  return () => {
    desktop.removeEventListener('change', onChange)
    tablet.removeEventListener('change', onChange)
  }
}

function getShelfColumns(): number {
  if (window.matchMedia(DESKTOP_QUERY).matches) {
    return 4
  }
  if (window.matchMedia(TABLET_QUERY).matches) {
    return 3
  }
  return 2
}

function getServerShelfColumns(): number {
  return 4
}

/**
 * 知识库浏览页：一架书。
 *
 * ## 有没有关键字是两种查询
 * 不带关键字走分页浏览（按更新时间倒序），每次 8 本、滚到底部再追加；
 * 带关键字走检索接口，语义优先、关键字补充，结果不分页、带相关度。
 * 两者的响应结构不同，所以书架数据分开存。
 *
 * ## mode 是后端告诉前端的"这次实际用了什么"
 * 语义检索依赖 Ollama，不可用时后端自动降级为关键字搜索，页面用黄色提示说明，
 * 而不是直接报错——降级后仍然能搜到东西。
 *
 * ## 顺序是固定的
 * 浏览按最近更新，检索按后端相关度。后端没有全量浏览排序，
 * 所以不提供"浏览最多"这种只能排当前页的选项。
 *
 * ## 详情会加浏览数
 * 详情接口每次调用都会 +1，抽屉把新值回传给外层就地更新封面，
 * 不再重拉列表——滚动加载后重拉第 1 页会把已翻出来的书收回去。
 */
export default function KnowledgeBase() {
  const { message } = AntdApp.useApp()
  const columns = useSyncExternalStore(
    subscribeShelfColumns,
    getShelfColumns,
    getServerShelfColumns,
  )

  const [categories, setCategories] = useState<KnowledgeCategory[]>([])
  const [rows, setRows] = useState<KnowledgeRow[]>([])
  const [searchItems, setSearchItems] = useState<KnowledgeSearchItem[]>([])
  const [searchMode, setSearchMode] = useState<SearchMode | null>(null)
  /** 全库文章数；只在未筛选的浏览请求里更新，搜索时不改动 */
  const [libraryTotal, setLibraryTotal] = useState<number | null>(null)
  /** 当前这一屏的总数：浏览时是分页总数，搜索时是命中数 */
  const [resultTotal, setResultTotal] = useState(0)
  const [latestUpdate, setLatestUpdate] = useState<string | null>(null)
  /** 已经取到第几页；滚动加载只往后追加，不再跳页 */
  const [current, setCurrent] = useState(1)
  const [draftKeyword, setDraftKeyword] = useState('')
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  /** 追加失败时停下来，交给用户点重试；否则哨兵会一直重试同一个坏请求 */
  const [loadMoreFailed, setLoadMoreFailed] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)

  const isSearching = keyword.length > 0

  // 只认最后一次请求。搜索有防抖、筛选又是点击即发，慢的那个响应可能后到；
  // 不丢弃就会把新条件的结果盖回旧条件的结果。
  const requestSeq = useRef(0)
  const sentinelRef = useRef<HTMLDivElement | null>(null)

  const load = useCallback(
    async ({
      silent = false,
      append = false,
      page = 1,
    }: {
      silent?: boolean
      /** true = 保留已上架的书，把这一页追加到后面 */
      append?: boolean
      /** 要取的页码，默认第 1 页 */
      page?: number
    } = {}) => {
      // 不做额外的“一次只跑一个”锁：requestSeq 已经保证只有最后一次追加会落到 state，
      // 重复触发最多浪费一个请求；加锁反而会在换筛选时被旧请求卡住。
      const seq = requestSeq.current + 1
      requestSeq.current = seq
      setLoadMoreFailed(false)
      if (append) {
        setLoadingMore(true)
      } else {
        // 重新查询（换词、换分类、刷新）时，上一次追加的 loading 也要一起收掉
        setLoadingMore(false)
        if (!silent) {
          setLoading(true)
        }
      }

      try {
        const query = keyword.trim()
        if (query) {
          const result = await searchKnowledge({ q: query, limit: SEARCH_LIMIT, categoryId })
          if (seq !== requestSeq.current) {
            return
          }
          setSearchItems(result.items)
          setSearchMode(result.mode)
          setRows([])
          setResultTotal(result.items.length)
          setHasMore(false)
          setCurrent(1)
        } else {
          const result = await listKnowledgeArticles({
            current: page,
            size: BROWSE_PAGE_SIZE,
            categoryId,
          })
          if (seq !== requestSeq.current) {
            return
          }
          if (append) {
            setRows((previous) => appendRows(previous, result.records))
          } else {
            setRows(result.records)
          }
          setResultTotal(result.total)
          setCurrent(page)
          // 后端说还有、且这一页确实拿到了数据，才继续往下取；
          // 空页停在原地，避免哨兵反复空转
          setHasMore(page * BROWSE_PAGE_SIZE < result.total && result.records.length > 0)
          setSearchItems([])
          setSearchMode(null)
          // 顶部统计说的是"整个知识库"，筛选分类时不跟着变
          if (!categoryId) {
            setLibraryTotal(result.total)
            if (page === 1) {
              setLatestUpdate(newestOf(result.records))
            }
          }
        }
      } catch (error) {
        if (seq !== requestSeq.current) {
          return
        }
        message.error(error instanceof Error ? error.message : '加载失败')
        // 追加失败时保留已经上架的书；重新查询失败才清空
        if (append) {
          setLoadMoreFailed(true)
        } else {
          setRows([])
          setResultTotal(0)
          setSearchItems([])
          setSearchMode(null)
          setHasMore(false)
        }
      } finally {
        // silent 请求也可能是一次非 silent 请求之后的最新一次，必须由它收尾 loading
        if (seq === requestSeq.current) {
          setLoading(false)
          setLoadingMore(false)
        }
      }
    },
    [keyword, categoryId, message],
  )

  const loadCategories = useCallback(async () => {
    try {
      setCategories(await listKnowledgeCategories())
    } catch {
      // 分类只是筛选条件，加载失败不阻塞书架；chip 只剩"全部"
    }
  }, [])

  useEffect(() => {
    // 挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect
    void loadCategories()
  }, [loadCategories])

  // 底部提示进入视口时取下一页。不用提前量：首屏仍然只装 8 本，
  // 用户真的往下滑了才开始追加。
  useEffect(() => {
    const node = sentinelRef.current
    if (!node || isSearching || loading || loadingMore || loadMoreFailed || !hasMore) {
      return
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void load({ append: true, page: current + 1 })
        }
      },
      { rootMargin: '0px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [current, hasMore, isSearching, load, loading, loadMoreFailed, loadingMore])

  // 输入即查：停顿一小会儿再发请求，避免每个字符打一次检索接口
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(draftKeyword.trim())
      // 换搜索词要回到第 1 页，否则可能停在"总数为 0 的第 5 页"
      setCurrent(1)
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [draftKeyword])

  const books = useMemo(() => {
    // 检索结果保持后端返回的相关度顺序，不要重排
    if (isSearching) {
      return searchItems
    }
    // 浏览固定按最近更新。后端已经是这个顺序，这里再排一次，
    // 避免以后换接口或分类筛选时书架空掉
    return [...rows].sort((left, right) => updateKey(right).localeCompare(updateKey(left)))
  }, [isSearching, rows, searchItems])

  const shelfRows = useMemo(() => chunk(books, columns), [books, columns])
  const skeletonRows = useMemo(
    () => chunk(Array.from({ length: columns * 2 }, (_, index) => index), columns),
    [columns],
  )
  const gridStyle = { '--shelf-columns': columns } as CSSProperties

  // NEW 只给已加载的书里最新的两本；搜索不再挂角标，
  // 否则九本书全带 NEW，等于没有"新"这个信息。
  const newIds = useMemo(() => {
    if (isSearching) {
      return new Set<string>()
    }
    return new Set(
      books
        .filter(isNewArticle)
        .sort((left, right) => publishKey(right).localeCompare(publishKey(left)))
        .slice(0, NEW_BADGE_LIMIT)
        .map((book) => book.id),
    )
  }, [books, isSearching])

  const commitSearch = () => {
    const next = draftKeyword.trim()
    if (next === keyword) {
      // 条件没变时"检索"当成刷新用，否则上次失败后再点一次不会有任何反应
      void load()
      return
    }
    setKeyword(next)
    setCurrent(1)
  }

  const selectCategory = (id: string | undefined) => {
    setCategoryId(id)
    setCurrent(1)
  }

  const clearFilters = () => {
    setDraftKeyword('')
    setKeyword('')
    setCategoryId(undefined)
    setCurrent(1)
  }

  const openDetail = (id: string) => {
    setDetailId(id)
    setDrawerOpen(true)
  }

  // 详情接口每次调用都会把浏览数 +1。抽屉把新值带回来，这里就地更新；
  // 不再重拉列表 —— 滚动加载后重拉第 1 页会把已经翻出来的书全收回去。
  const markViewed = useCallback((id: string, viewCount: number) => {
    setRows((previous) => previous.map((row) => (row.id === id ? { ...row, viewCount } : row)))
    setSearchItems((previous) =>
      previous.map((row) => (row.id === id ? { ...row, viewCount } : row)),
    )
  }, [])

  const closeDetail = () => {
    setDrawerOpen(false)
  }

  const hasFilters = isSearching || categoryId !== undefined

  const activeCategory = categories.find((category) => category.id === categoryId)
  const collectionName = activeCategory?.name ?? '全部藏书'
  const collectionYear = new Date().getFullYear()
  const shelfCount = isSearching ? books.length : resultTotal
  // 排序不再是可选项：浏览按最近更新，检索按后端相关度
  const orderLabel = isSearching ? '按相关度' : '按最近更新'
  // 状态行只在真的有话可说时出现，否则不要留空行，书架层级交给 collection head
  const hasStatusNotes =
    (isSearching && searchMode === 'SEMANTIC') || (isSearching && books.length >= SEARCH_LIMIT)

  return (
    <section className="knowledge-library">
      <div className="library-inner">
        <header className="library-head">
          <div className="library-heading">
            <p className="library-eyebrow">KNOWLEDGE LIBRARY</p>
            <h1 className="library-title">运维知识库</h1>
            <p className="library-subtitle">故障处置、日常运维与现场经验，按主题上架，随手抽一本。</p>
          </div>

          <dl className="library-stats">
            <div className="library-stat">
              <dt>文章总数</dt>
              <dd>{libraryTotal ?? '—'}</dd>
            </div>
            <div className="library-stat">
              <dt>分类</dt>
              <dd>{categories.length}</dd>
            </div>
            <div className="library-stat">
              <dt>最近更新</dt>
              <dd className="library-stat-date">{latestUpdate ? shortDate(latestUpdate) : '—'}</dd>
            </div>
          </dl>
        </header>

        <div className="library-toolbar">
          <form
            className="library-search"
            role="search"
            onSubmit={(event) => {
              event.preventDefault()
              commitSearch()
            }}
          >
            <Input
              size="large"
              allowClear
              prefix={<SearchOutlined aria-hidden="true" />}
              placeholder="搜索故障现象、处理方法、设备名称…"
              value={draftKeyword}
              onChange={(event) => setDraftKeyword(event.target.value)}
              aria-label="搜索知识文章"
            />
            <button type="submit" className="library-submit">
              检索
            </button>
          </form>
        </div>

        <div className="library-chips" role="group" aria-label="按分类筛选">
          <button
            type="button"
            className="chip"
            aria-pressed={categoryId === undefined}
            onClick={() => selectCategory(undefined)}
          >
            全部
            <span className="chip-count">{libraryTotal ?? resultTotal}</span>
          </button>
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className="chip"
              aria-pressed={categoryId === category.id}
              onClick={() => selectCategory(category.id)}
            >
              {category.name}
              <span className="chip-count">{category.publishedCount}</span>
            </button>
          ))}
        </div>

        {isSearching && searchMode === 'KEYWORD' ? (
          <Alert
            className="library-alert"
            type="warning"
            showIcon
            message="语义检索暂不可用，已降级为关键字搜索"
          />
        ) : null}

        {hasStatusNotes ? (
          <div className="library-status" role="status">
            {isSearching && searchMode === 'SEMANTIC' ? (
              <span className="library-mode">语义检索</span>
            ) : null}
            {isSearching && books.length >= SEARCH_LIMIT ? (
              <span className="library-hint">检索最多返回 {SEARCH_LIMIT} 篇</span>
            ) : null}
          </div>
        ) : null}

        <div className="library-collection">
          <div className="collection-heading">
            <p className="collection-eyebrow">COLLECTION / {collectionYear}</p>
            <h2 className="collection-title">{collectionName}</h2>
          </div>
          <div className="collection-meta">
            <span>{loading ? '加载中' : `${shelfCount} 篇`}</span>
            <span className="collection-sort">{orderLabel}</span>
          </div>
        </div>

        <div className="shelf" aria-busy={loading}>
          {loading ? (
            <>
              <span className="sr-only" role="status">
                正在加载书架
              </span>
              {skeletonRows.map((row, rowIndex) => (
                <div className="shelf-row" key={`skeleton-row-${rowIndex}`}>
                  <div className="shelf-books" style={gridStyle}>
                    {row.map((key) => (
                      <BookSkeleton key={`skeleton-book-${key}`} />
                    ))}
                  </div>
                  <span className="shelf-plank" aria-hidden="true" />
                </div>
              ))}
            </>
          ) : null}

          {!loading && books.length === 0 ? (
            <ShelfEmpty hasFilters={hasFilters} onClear={clearFilters} />
          ) : null}

          {!loading
            ? shelfRows.map((row) => (
                <div className="shelf-row" key={row[0]?.id ?? 'row'}>
                  <div className="shelf-books" style={gridStyle}>
                    {row.map((book) => (
                      <ShelfBook
                        key={book.id}
                        book={book}
                        isNew={newIds.has(book.id)}
                        onOpen={openDetail}
                      />
                    ))}
                  </div>
                  <span className="shelf-plank" aria-hidden="true" />
                </div>
              ))
            : null}
        </div>

        {!isSearching && !loading && books.length > 0 && (hasMore || loadingMore || loadMoreFailed) ? (
          <div className="library-more" ref={sentinelRef} role="status" aria-live="polite">
            {loadMoreFailed ? (
              <button
                type="button"
                className="library-more-btn"
                onClick={() => {
                  setLoadMoreFailed(false)
                  void load({ append: true, page: current + 1 })
                }}
              >
                加载失败，点击重试
              </button>
            ) : loadingMore ? (
              <>
                <Spin size="small" />
                <span>正在加载更多…</span>
              </>
            ) : (
              <span>继续下滑，加载更多</span>
            )}
          </div>
        ) : null}

        <KnowledgeDetailDrawer
          open={drawerOpen}
          articleId={detailId}
          onClose={closeDetail}
          onViewed={markViewed}
        />
      </div>
    </section>
  )
}

interface ShelfBookProps {
  book: KnowledgeRow
  /** 是否挂 NEW；由外层统一限量，单本书不自己判断 */
  isNew: boolean
  onOpen: (id: string) => void
}

/**
 * 一本正面朝外的书。
 *
 * 封面不显示摘要 —— 摘要只属于抽屉；这里只回答"是哪一本、什么时候更新的、热不热"。
 * 颜色和纹理由分类 id 推导：同一分类的书摆在一起是一套，分类一多也不会彩虹化。
 */
function ShelfBook({ book, isNew, onOpen }: ShelfBookProps) {
  const hash = hashOf(book.categoryId || book.categoryName || book.id)
  const tone = hash % 6
  const texture = Math.floor(hash / 6) % 4
  const category = book.categoryName ?? '未分类'

  return (
    <button
      type="button"
      className="book"
      onClick={() => onOpen(book.id)}
      aria-label={`打开《${book.title}》，分类 ${category}`}
    >
      <span className="book-cover" data-tone={tone} data-texture={texture}>
        {isNew ? <span className="book-new">NEW</span> : null}
        <span className="book-face">
          <span className="book-category">{category}</span>
          <span className="book-title">{book.title}</span>
          <span className="book-meta">
            <span>{shortDate(book.updateTime)}</span>
            <span className="book-views">
              <EyeOutlined aria-hidden="true" /> {book.viewCount}
            </span>
          </span>
        </span>
      </span>
    </button>
  )
}

function BookSkeleton() {
  return (
    <div className="book book-skeleton" aria-hidden="true">
      <div className="book-cover">
        <div className="book-face">
          <span className="sk-line sk-line-short" />
          <span className="sk-line" />
          <span className="sk-line sk-line-medium" />
          <span className="sk-line sk-line-meta" />
        </div>
      </div>
    </div>
  )
}

function ShelfEmpty({ hasFilters, onClear }: { hasFilters: boolean; onClear: () => void }) {
  return (
    <div className="shelf-empty">
      <p className="shelf-empty-title">这一格还空着</p>
      <p className="shelf-empty-text">
        {hasFilters
          ? '换个关键字或分类试试，也可以把筛选清掉。'
          : '等第一篇文章上架后，这里就有书可抽了。'}
      </p>
      {hasFilters ? (
        <Button className="shelf-empty-btn" onClick={onClear}>
          清除筛选
        </Button>
      ) : null}
    </div>
  )
}

function chunk<T>(items: T[], groupSize: number): T[][] {
  const groups: T[][] = []
  for (let index = 0; index < items.length; index += groupSize) {
    groups.push(items.slice(index, index + groupSize))
  }
  return groups
}

/** 追加下一页时按 id 去重：接口数据被并发修改时，同一本书可能跨页出现 */
function appendRows(previous: KnowledgeRow[], next: KnowledgeRow[]): KnowledgeRow[] {
  if (next.length === 0) {
    return previous
  }
  const seen = new Set(previous.map((row) => row.id))
  const fresh = next.filter((row) => !seen.has(row.id))
  return fresh.length > 0 ? [...previous, ...fresh] : previous
}

function hashOf(value: string): number {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  }
  return hash
}

function updateKey(book: KnowledgeRow): string {
  return formatDateTime(book.updateTime)
}

function shortDate(raw: string | null): string {
  const text = formatDateTime(raw)
  return text === '-' ? '未记录' : text.slice(0, 10)
}

/** 当前页里最新的更新时间。列表本身按更新时间倒序，但分类筛选后不保证，还是真的比一遍 */
function newestOf(books: KnowledgeRow[]): string | null {
  let latest: string | null = null
  let latestKey = ''
  for (const book of books) {
    const key = updateKey(book)
    if (key !== '-' && key > latestKey) {
      latestKey = key
      latest = book.updateTime
    }
  }
  return latest
}

/** 上架时间：优先发布时间，没发布过就退回更新时间 */
function publishKey(book: KnowledgeRow): string {
  return formatDateTime(book.publishTime ?? book.updateTime)
}

/** 用日期字符串比较，避开不同浏览器对 "YYYY-MM-DD HH:mm:ss" 的解析差异 */
function isNewArticle(book: KnowledgeRow): boolean {
  const text = publishKey(book)
  if (text === '-') {
    return false
  }
  return text.slice(0, 10) >= daysAgo(NEW_WINDOW_DAYS)
}

function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
