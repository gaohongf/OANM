import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Card,
  Empty,
  Segmented,
  Skeleton,
  Space,
  Tooltip,
} from 'antd'
import {
  BellOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileAddOutlined,
  InboxOutlined,
  MessageOutlined,
  ReloadOutlined,
  RightOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
  TeamOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router'

import { AI_PERMISSIONS } from '../api/ai'
import { fetchWorkOrderStats, WORK_ORDER_PERMISSIONS } from '../api/workOrder'
import type {
  DailyWorkOrderCount,
  RecentWorkOrder,
  WorkOrderStats,
  WorkOrderStatus,
} from '../api/workOrder'
import { hasPermission } from '../permission/match'
import { useAuthStore } from '../store/auth'
import { useNotificationsStore } from '../store/notifications'
import { KpiCard } from './dashboard/components/KpiCard'
import { Sparkline } from './dashboard/components/Sparkline'
import { StatusDonut } from './dashboard/components/StatusDonut'
import { TrendChart } from './dashboard/components/TrendChart'
import { CHART_COLORS } from './dashboard/palette'
import styles from './dashboard/Dashboard.module.css'

/**
 * 首页 / 概览。
 *
 * ## 这一页对"零权限用户"仍然要能打开
 * 旧的概览刻意不请求任何接口，就是为了让一个刚创建、还没被授权的账号登录后
 * 不会看到一个报错的首页。新版本保留了这个不变量：统计数据是一个**独立的权限**，
 * 没有它时页面退化成问候语 + 快捷入口，而不是整页 403。
 *
 * ## 数字从哪来
 * 状态数量、按天趋势、最近动态都来自 `GET /api/ops/work_order/stats` 一次请求。
 * 未读数来自消息 store —— 它和顶栏铃铛是同一个数字，不在这里另数一遍。
 *
 * ## 哪些是近似值
 * 目前没有"解决/关闭"接口，也没有完成时间字段。`todayCompleted` 与趋势里的完成线
 * 用的是"已解决/已关闭单据的 update_time"这个近似口径（后端注释里有说明）。
 * 界面如实显示 0，不用模拟数据填满图表。
 */

/** 快捷入口用到的权限键。与后端 permission_key 逐字一致 */
const USER_LIST_PERMISSION = 'GET:/api/auth/users'
const ROLE_LIST_PERMISSION = 'GET:/api/auth/roles'

const MESSAGE_CENTER_PATH = '/message/list'
const WORK_ORDER_PATH = '/ops/work-order'
const INTENT_PATH = '/ops/ai/intent'

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/** 最近动态的状态文案与图标 */
const STATUS_ACTIVITY: Record<WorkOrderStatus, { label: string; icon: ReactNode; color: string }> = {
  NEW: { label: '待受理', icon: <FileAddOutlined />, color: CHART_COLORS.blue },
  ACCEPTED: { label: '已受理', icon: <ClockCircleOutlined />, color: CHART_COLORS.orange },
  RESOLVED: { label: '已解决', icon: <CheckCircleOutlined />, color: CHART_COLORS.aqua },
  CLOSED: { label: '已关闭', icon: <StopOutlined />, color: CHART_COLORS.neutral },
}

interface QuickLink {
  key: string
  title: string
  desc: string
  icon: ReactNode
  color: string
  path: string
}

export default function Dashboard() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()

  const permissions = useAuthStore((state) => state.permissions)
  const user = useAuthStore((state) => state.user)
  const unreadCount = useNotificationsStore((state) => state.unreadCount)
  const notificationsLoaded = useNotificationsStore((state) => state.loaded)

  const canViewStats = hasPermission(permissions, WORK_ORDER_PERMISSIONS.stats)
  const canCreate = hasPermission(permissions, AI_PERMISSIONS.intentInference)
  const canListWorkOrders = hasPermission(permissions, WORK_ORDER_PERMISSIONS.list)
  const canOpenDetail = hasPermission(permissions, WORK_ORDER_PERMISSIONS.detail)

  const [range, setRange] = useState<7 | 30>(7)
  const [stats, setStats] = useState<WorkOrderStats | null>(null)
  const [initialLoading, setInitialLoading] = useState(canViewStats)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showTable, setShowTable] = useState(false)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)

  /**
   * 请求序号。
   *
   * 切换 7/30 天时两个请求可能并发；没有它的话，先发后到的旧结果会把新结果覆盖掉，
   * 表现是"点了 30 天，图表又跳回 7 天"。只接受最后一次请求的结果。
   */
  const requestIdRef = useRef(0)

  /**
   * 只发请求，不负责把 loading 打开 —— 打开 loading 是用户动作（挂载、切换范围、点刷新）
   * 的后果，在这里统一设会让 effect 同步 setState，触发多余的级联渲染。
   */
  const loadStats = useCallback(
    async (days: 7 | 30): Promise<boolean> => {
      if (!canViewStats) {
        return false
      }
      const requestId = requestIdRef.current + 1
      requestIdRef.current = requestId

      try {
        const data = await fetchWorkOrderStats(days)
        if (requestId !== requestIdRef.current) {
          return false
        }
        setStats(data)
        setError(null)
        setUpdatedAt(new Date())
        return true
      } catch (failure) {
        if (requestId !== requestIdRef.current) {
          return false
        }
        setError(failure instanceof Error ? failure.message : '概览数据加载失败')
        return false
      } finally {
        if (requestId === requestIdRef.current) {
          setInitialLoading(false)
          setRefreshing(false)
        }
      }
    },
    [canViewStats],
  )

  useEffect(() => {
    if (!canViewStats) {
      return
    }
    // 取数属于"与外部系统同步"；状态更新都在 await 之后，不会在这次渲染里级联。
    // oxlint-disable-next-line react/set-state-in-effect
    void loadStats(range)
  }, [canViewStats, range, loadStats])

  const onRefresh = async () => {
    let ok = true
    if (canViewStats) {
      if (stats === null) {
        setInitialLoading(true)
      } else {
        setRefreshing(true)
      }
      ok = await loadStats(range)
    }
    // 刷新按钮同时刷新未读消息：用户点的是"整页刷新"，不是"只刷新工单"
    await useNotificationsStore.getState().refresh()
    if (ok) {
      message.success('已刷新')
    } else {
      message.warning('数据刷新失败，请稍后重试')
    }
  }

  const onMarkAllRead = async () => {
    try {
      await useNotificationsStore.getState().markAllRead()
      message.success('已全部标为已读')
    } catch (failure) {
      message.error(failure instanceof Error ? failure.message : '操作失败')
    }
  }

  const now = new Date()
  const greeting = greetingOf(now.getHours())
  const displayName = user?.nickname || user?.username || '同事'
  const dateText = `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 · ${
    WEEKDAYS[now.getDay()]
  }`

  const status = (() => {
    if (!canViewStats) {
      return { dot: styles.statusDotMuted, text: '概览权限未开通' }
    }
    if (initialLoading && stats === null) {
      return { dot: styles.statusDotMuted, text: '正在加载数据' }
    }
    if (error) {
      return {
        dot: styles.statusDotWarn,
        text: stats === null ? '数据加载异常' : '数据刷新失败，显示上次结果',
      }
    }
    return { dot: styles.statusDotGood, text: '概览数据已同步' }
  })()

  const summary = (() => {
    if (!canViewStats) {
      return '当前账号还没有查看工单概览的权限，可以从下方快捷入口继续。'
    }
    if (initialLoading && stats === null) {
      return '正在加载今天的工单数据……'
    }
    if (stats === null) {
      return '概览数据暂时不可用，请稍后刷新重试。'
    }
    if (stats.pending > 0) {
      return stats.accepted > 0
        ? `当前有 ${stats.pending} 张工单待受理，另有 ${stats.accepted} 张已受理。`
        : `当前有 ${stats.pending} 张工单待受理。`
    }
    if (stats.accepted > 0) {
      return `待处理工单已经清空，${stats.accepted} 张工单正在处理中。`
    }
    return '当前没有待处理的工单，可以先看看最近的流转情况。'
  })()

  const quickLinks: QuickLink[] = [
    {
      key: 'create',
      title: '新建工单',
      desc: '用对话创建工单',
      icon: <RobotOutlined />,
      color: CHART_COLORS.blue,
      path: INTENT_PATH,
    },
    {
      key: 'users',
      title: '用户管理',
      desc: '管理账号',
      icon: <TeamOutlined />,
      color: CHART_COLORS.aqua,
      path: '/system/users',
    },
    {
      key: 'roles',
      title: '角色权限',
      desc: '配置访问范围',
      icon: <SafetyCertificateOutlined />,
      color: CHART_COLORS.orange,
      path: '/system/roles',
    },
    {
      key: 'messages',
      title: '消息中心',
      desc: '查看未读消息',
      icon: <MessageOutlined />,
      color: CHART_COLORS.violet,
      path: MESSAGE_CENTER_PATH,
    },
  ].filter((link) => {
    if (link.key === 'create') {
      return canCreate
    }
    if (link.key === 'users') {
      return hasPermission(permissions, USER_LIST_PERMISSION)
    }
    if (link.key === 'roles') {
      return hasPermission(permissions, ROLE_LIST_PERMISSION)
    }
    return true
  })

  const quickEntryCard = (
    <Card
      className={`${styles.card} ${styles.fillCard}`}
      styles={{ body: { padding: 16, display: 'flex', flexDirection: 'column' } }}
    >
      <div className={styles.cardHeader}>
        <div>
          <div className={styles.cardTitle}>快捷入口</div>
          <div className={styles.cardSubtitle}>常用功能，直达</div>
        </div>
      </div>

      {quickLinks.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="当前账号没有可用的快捷入口"
          style={{ padding: '20px 0' }}
        />
      ) : (
        <div className={styles.quickGrid}>
          {quickLinks.map((link) => (
            <button
              key={link.key}
              type="button"
              className={styles.quickCard}
              onClick={() => navigate(link.path)}
              aria-label={`${link.title}：${link.desc}`}
            >
              <span
                className={styles.quickIcon}
                style={{ color: link.color, background: `${link.color}14` }}
                aria-hidden="true"
              >
                {link.icon}
              </span>
              <span className={styles.quickText}>
                <span className={styles.quickTitle}>
                  {link.title}
                  {link.key === 'messages' && unreadCount > 0 && (
                    <Badge count={unreadCount} size="small" />
                  )}
                </span>
                <span className={styles.quickDesc}>{link.desc}</span>
              </span>
              <RightOutlined className={styles.quickArrow} aria-hidden="true" />
            </button>
          ))}
        </div>
      )}

      {/* 系统状态条：把卡片底部压住，也让"这份数字是什么时候的"有处可查 */}
      {stats !== null && (
        <div className={styles.quickFooter}>
          <span className={styles.quickFooterDot} aria-hidden="true" />
          <span>{updatedAt ? `数据更新于 ${formatClock(updatedAt)}` : '数据已同步'}</span>
          <span className={styles.quickFooterSpacer} />
          <span>共 {stats.total} 张工单</span>
        </div>
      )}
    </Card>
  )

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <div className={styles.heroMain}>
          <div className={styles.statusLine}>
            <i className={`${styles.statusDot} ${status.dot}`} aria-hidden="true" />
            <span>{status.text}</span>
            <span aria-hidden="true">·</span>
            <span className={styles.dateText}>{dateText}</span>
          </div>
          <h1 className={styles.greeting}>
            {greeting}，{displayName}
          </h1>
          <div className={styles.summary}>{summary}</div>
        </div>

        <div className={styles.actions}>
          <Tooltip title="刷新概览">
            <Button
              aria-label="刷新概览"
              icon={<ReloadOutlined />}
              loading={refreshing || initialLoading}
              onClick={() => void onRefresh()}
            />
          </Tooltip>
          {canCreate && (
            <Button
              // 有工单列表权限时，主 CTA 是"查看工单"；否则提单才是这一页唯一能做的事
              type={canListWorkOrders ? 'default' : 'primary'}
              icon={<RobotOutlined />}
              onClick={() => navigate(INTENT_PATH)}
            >
              智能提单
            </Button>
          )}
          {canListWorkOrders && (
            <Button
              type="primary"
              icon={<UnorderedListOutlined />}
              onClick={() => navigate(WORK_ORDER_PATH)}
            >
              查看工单
            </Button>
          )}
        </div>
      </header>

      {error && stats !== null && (
        <Alert
          className={styles.errorBanner}
          type="warning"
          showIcon
          message="概览数据加载失败"
          description={error}
          action={
            <Button size="small" onClick={() => void onRefresh()}>
              重试
            </Button>
          }
        />
      )}

      {canViewStats ? (
        initialLoading && stats === null ? (
          <StatsSkeleton />
        ) : stats === null ? (
          <Card className={styles.card} styles={{ body: { padding: 32 } }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={error ?? '概览数据暂时不可用'}
            >
              <Button onClick={() => void onRefresh()}>重试</Button>
            </Empty>
          </Card>
        ) : (
          <div className={`${styles.statsBody} ${refreshing ? styles.stale : ''}`}>
            <div className={styles.kpiGrid}>
              <KpiCard
                icon={<InboxOutlined />}
                label="待处理工单"
                value={stats.pending}
                unit="张"
                accent={CHART_COLORS.blue}
                hint={
                  stats.total === 0
                    ? '还没有工单'
                    : stats.accepted > 0
                      ? `另有 ${stats.accepted} 张已受理`
                      : '暂无已受理工单'
                }
                visual={<PendingComposition pending={stats.pending} accepted={stats.accepted} />}
              />

              <KpiCard
                icon={<FileAddOutlined />}
                label="本周新增"
                value={stats.weekCreated}
                unit="张"
                accent={CHART_COLORS.blue}
                hint={<WeekDelta current={stats.weekCreated} previous={stats.lastWeekCreated} />}
                visual={
                  <Sparkline
                    values={stats.daily.map((day) => day.created)}
                    color={CHART_COLORS.blue}
                  />
                }
              />

              <KpiCard
                icon={<CheckCircleOutlined />}
                label="今日已完成"
                value={stats.todayCompleted}
                unit="张"
                accent={CHART_COLORS.aqua}
                hint={
                  <DayDelta current={stats.todayCompleted} previous={stats.yesterdayCompleted} />
                }
                visual={
                  <Sparkline
                    values={stats.daily.map((day) => day.completed)}
                    color={CHART_COLORS.aqua}
                  />
                }
              />

              <KpiCard
                icon={<BellOutlined />}
                label="未读消息"
                value={notificationsLoaded ? unreadCount : '—'}
                unit={notificationsLoaded ? '条' : ''}
                accent={CHART_COLORS.violet}
                hint={
                  <Space size={4} wrap>
                    <Button
                      type="link"
                      size="small"
                      style={{ padding: 0, height: 'auto' }}
                      disabled={unreadCount === 0}
                      onClick={() => void onMarkAllRead()}
                    >
                      全部已读
                    </Button>
                    <span style={{ color: 'rgba(0, 0, 0, 0.25)' }}>·</span>
                    <Button
                      type="link"
                      size="small"
                      style={{ padding: 0, height: 'auto' }}
                      onClick={() => navigate(MESSAGE_CENTER_PATH)}
                    >
                      消息中心
                    </Button>
                  </Space>
                }
              />
            </div>

            <div className={styles.mainGrid}>
              <Card
                className={`${styles.card} ${styles.sectionCard} ${styles.fillCard}`}
                styles={{ body: { padding: 18 } }}
              >
                <div className={styles.cardHeader}>
                  <div>
                    <div className={styles.cardTitle}>工单趋势</div>
                    <div className={styles.cardSubtitle}>新增与完成 · 按日统计</div>
                  </div>
                  <div className={styles.cardExtra}>
                    <Segmented<7 | 30>
                      value={range}
                      options={[
                        { label: '近 7 天', value: 7 },
                        { label: '近 30 天', value: 30 },
                      ]}
                      onChange={(value) => {
                        // loading 在事件里打开：effect 只负责取数
                        if (stats === null) {
                          setInitialLoading(true)
                        } else {
                          setRefreshing(true)
                        }
                        setRange(value)
                      }}
                    />
                    <Button type="text" size="small" onClick={() => setShowTable((value) => !value)}>
                      {showTable ? '收起数据表' : '数据表'}
                    </Button>
                  </div>
                </div>

                <div className={styles.trendFigures}>
                  <div className={styles.trendFigure}>
                    <span className={styles.trendFigureValue}>
                      {sumOf(stats.daily, (day) => day.created)}
                    </span>
                    <span className={styles.trendFigureLabel}>新增</span>
                  </div>
                  <div className={styles.trendFigure}>
                    <span className={styles.trendFigureValue}>
                      {sumOf(stats.daily, (day) => day.completed)}
                    </span>
                    <span className={styles.trendFigureLabel}>已完成</span>
                  </div>
                </div>

                <TrendChart key={range} daily={stats.daily} />
                {showTable && <TrendTable daily={stats.daily} />}
              </Card>

              <Card
                className={`${styles.card} ${styles.sectionCard} ${styles.fillCard}`}
                styles={{
                  body: { padding: 18, display: 'flex', flexDirection: 'column' },
                }}
              >
                <div className={styles.cardHeader}>
                  <div>
                    <div className={styles.cardTitle}>工单状态</div>
                    <div className={styles.cardSubtitle}>当前工单实时分布</div>
                  </div>
                  {canListWorkOrders && (
                    <Button type="link" size="small" onClick={() => navigate(WORK_ORDER_PATH)}>
                      查看全部 <RightOutlined />
                    </Button>
                  )}
                </div>

                <StatusDonut
                  pending={stats.pending}
                  accepted={stats.accepted}
                  resolved={stats.resolved}
                  closed={stats.closed}
                />
              </Card>
            </div>

            <div className={styles.bottomGrid}>
              <Card
                className={`${styles.card} ${styles.sectionCard} ${styles.fillCard}`}
                styles={{
                  body: { padding: 16, display: 'flex', flexDirection: 'column' },
                }}
              >
                <div className={styles.cardHeader}>
                  <div>
                    <div className={styles.cardTitle}>最近工单动态</div>
                    <div className={styles.cardSubtitle}>按最近更新时间排序</div>
                  </div>
                  {canListWorkOrders && (
                    <Button type="link" size="small" onClick={() => navigate(WORK_ORDER_PATH)}>
                      查看全部 <RightOutlined />
                    </Button>
                  )}
                </div>

                {stats.recent.length === 0 ? (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无工单动态"
                    style={{ padding: '24px 0' }}
                  />
                ) : (
                  <div className={styles.activityList}>
                    {stats.recent.slice(0, 5).map((item) => (
                      <ActivityRow
                        key={item.id}
                        item={item}
                        clickable={canOpenDetail}
                        onOpen={() =>
                          navigate(`${WORK_ORDER_PATH}?openId=${encodeURIComponent(item.id)}`)
                        }
                      />
                    ))}
                  </div>
                )}
              </Card>

              {quickEntryCard}
            </div>
          </div>
        )
      ) : (
        <Card className={styles.card} styles={{ body: { padding: 32 } }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="当前账号还没有查看工单概览的权限"
          >
            <div style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: 13 }}>
              请联系管理员在「角色管理」中开通「概览统计」权限；下方快捷入口仍可正常使用。
            </div>
          </Empty>
        </Card>
      )}

      {/* 没有统计数据时，快捷入口单独占一行；有统计时它已经在底部区域里了 */}
      {!(canViewStats && stats !== null) && quickEntryCard}
    </div>
  )
}

/* ---------- 局部小组件 ---------- */

function StatsSkeleton() {
  return (
    <>
      <div className={styles.loadingGrid}>
        {[0, 1, 2, 3].map((key) => (
          <Card key={key} className={styles.kpiCard} styles={{ body: { padding: 18 } }}>
            <Skeleton active title={false} paragraph={{ rows: 2 }} />
          </Card>
        ))}
      </div>
      <div className={styles.mainGrid}>
        <Card className={`${styles.card} ${styles.sectionCard}`} styles={{ body: { padding: 20 } }}>
          <Skeleton active paragraph={{ rows: 7 }} />
        </Card>
        <Card className={`${styles.card} ${styles.sectionCard}`} styles={{ body: { padding: 20 } }}>
          <Skeleton active paragraph={{ rows: 7 }} />
        </Card>
      </div>
    </>
  )
}

function PendingComposition({ pending, accepted }: { pending: number; accepted: number }) {
  if (pending + accepted === 0) {
    return <span className={styles.stackLabels}>暂无受理记录</span>
  }
  return (
    <div>
      {/* 构成条只做视觉提示；下面的文字才是可读的数据 */}
      <div className={styles.stackBar} aria-hidden="true">
        <span style={{ flexGrow: pending, background: CHART_COLORS.blue }} />
        <span style={{ flexGrow: accepted, background: CHART_COLORS.orange }} />
      </div>
      <div className={styles.stackLabels}>
        待受理 {pending} · 已受理 {accepted}
      </div>
    </div>
  )
}

function WeekDelta({ current, previous }: { current: number; previous: number }) {
  if (current === 0 && previous === 0) {
    return <>本周还没有新增</>
  }
  if (previous === 0) {
    return <>上周无新增</>
  }
  const delta = current - previous
  if (delta === 0) {
    return <>与上周持平</>
  }
  return <>{`较上周 ${delta > 0 ? '+' : ''}${delta} 张`}</>
}

function DayDelta({ current, previous }: { current: number; previous: number }) {
  if (current === 0) {
    return <>今日暂无完成记录</>
  }
  if (previous === 0) {
    return <>昨日无完成记录</>
  }
  const delta = current - previous
  if (delta === 0) {
    return <>与昨日持平</>
  }
  return <>{`较昨日 ${delta > 0 ? '+' : ''}${delta} 张`}</>
}

function ActivityRow({
  item,
  clickable,
  onOpen,
}: {
  item: RecentWorkOrder
  clickable: boolean
  onOpen: () => void
}) {
  // 后端出现未知状态时如实显示原始值，而不是借用某个最像的文案
  const activity = STATUS_ACTIVITY[item.status] ?? {
    label: item.status,
    icon: <ClockCircleOutlined />,
    color: CHART_COLORS.axis,
  }

  return (
    <button
      type="button"
      className={styles.activityButton}
      disabled={!clickable}
      onClick={onOpen}
      aria-label={`${activity.label}：${item.title}`}
    >
      <span
        className={styles.activityIcon}
        style={{ color: activity.color, background: `${activity.color}14` }}
        aria-hidden="true"
      >
        {activity.icon}
      </span>
      <span className={styles.activityBody}>
        <span className={styles.activityText} style={{ display: 'block' }}>
          工单「{item.title}」{activity.label}
        </span>
        <span className={styles.activityMeta} title={`完整 ID：${item.id}`}>
          #{shortId(item.id)}
        </span>
      </span>
      <span className={styles.activityTime}>{formatRelativeTime(item.updateTime)}</span>
    </button>
  )
}

function TrendTable({ daily }: { daily: DailyWorkOrderCount[] }) {
  if (daily.length === 0) {
    return null
  }
  return (
    <div className={styles.tableWrap}>
      <table className={styles.dataTable}>
        <caption className={styles.srOnly}>工单趋势数据</caption>
        <thead>
          <tr>
            <th scope="col">日期</th>
            <th scope="col">新增</th>
            <th scope="col">已完成</th>
          </tr>
        </thead>
        <tbody>
          {daily.map((day) => (
            <tr key={day.date}>
              <td>{day.date}</td>
              <td>{day.created}</td>
              <td>{day.completed}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ---------- 纯函数 ---------- */

function greetingOf(hour: number): string {
  if (hour < 5) {
    return '夜深了'
  }
  if (hour < 11) {
    return '早上好'
  }
  if (hour < 13) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
}

function sumOf(daily: DailyWorkOrderCount[], pick: (day: DailyWorkOrderCount) => number): number {
  return daily.reduce((sum, day) => sum + pick(day), 0)
}

/** 雪花 id 只显示后 6 位，完整值放在 title 里。它只是给人一个可区分的短号 */
function shortId(id: string): string {
  return id.length > 6 ? id.slice(-6) : id
}

/**
 * 相对时间。
 *
 * 后端给的是不带时区的 `LocalDateTime`（形如 `2026-10-22T18:03:00`），
 * 浏览器按本地时区解析；这与数据库和服务端在同一台机器/同一时区的部署方式一致。
 */
function formatRelativeTime(raw: unknown): string {
  if (typeof raw !== 'string' || raw === '') {
    return '-'
  }
  const date = new Date(raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) {
    return raw
  }
  const diff = Date.now() - date.getTime()
  if (diff < 0) {
    return '刚刚'
  }
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) {
    return '刚刚'
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} 小时前`
  }
  const days = Math.floor(hours / 24)
  if (days < 7) {
    return `${days} 天前`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 更新时间只显示到分钟：秒级差异对"这份数据新不新"没有意义 */
function formatClock(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}
