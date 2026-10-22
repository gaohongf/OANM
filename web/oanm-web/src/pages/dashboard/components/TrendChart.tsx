import { useEffect, useRef, useState } from 'react'

import type { DailyWorkOrderCount } from '../../../api/workOrder'
import { CHART_COLORS } from '../palette'
import styles from '../Dashboard.module.css'

interface TrendChartProps {
  /** 按日期升序；后端已经补过没有数据的日子 */
  daily: DailyWorkOrderCount[]
}

/** 画布内边距。左侧要留出纵轴标签的位置 */
const MARGIN = { top: 14, right: 16, bottom: 26, left: 38 }

/** 纵轴固定 4 段。段数太多会让 7 个点的折线看起来像心电图 */
const Y_TICK_COUNT = 4

/**
 * 新增 / 完成的双线趋势图。
 *
 * 纯 SVG，没有引入图表库：这里只需要两条折线、四条网格和一个十字准星。
 * 为此装一个几百 KB 的依赖，收益抵不过构建体积和升级成本。
 *
 * ## 为什么宽度是量出来的
 * 用固定 `viewBox` 再让 SVG 等比缩放，窄屏上 11px 的轴标签会被缩到 5px 左右。
 * 所以用 ResizeObserver 拿实际宽度，按真实像素算坐标，文字始终保持可读。
 *
 * ## 交互不是可选项
 * 鼠标沿横轴移动时吸附到最近的一天（读者瞄的是日期，不是 2px 的线），
 * 十字准星与 Tooltip 同时给出两条线的值；键盘可以用方向键做同样的事。
 * 精确数值另有数据表，不依赖悬停才能读到。
 */
export function TrendChart({ daily }: TrendChartProps) {
  const wrapRef = useRef<HTMLDivElement | null>(null)
  const [width, setWidth] = useState(640)
  const [active, setActive] = useState<number | null>(null)

  useEffect(() => {
    const element = wrapRef.current
    if (!element) {
      return
    }
    const observer = new ResizeObserver((entries) => {
      const next = entries[0]?.contentRect.width
      // 回调里更新宽度是 ResizeObserver 的正常用法；effect 本身不同步 setState，
      // 所以不会出现"挂载 → 立即重渲染"的级联。
      if (next && next > 0) {
        setWidth(next)
      }
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  // 主区高度压在 340–360：绘图区 200，窄屏再让 20px 给日期标签
  const height = width < 520 ? 180 : 200
  const plotWidth = Math.max(1, width - MARGIN.left - MARGIN.right)
  const plotHeight = Math.max(1, height - MARGIN.top - MARGIN.bottom)
  const lastIndex = daily.length - 1

  const maxValue = Math.max(1, ...daily.flatMap((day) => [day.created, day.completed]))
  const tickStep = niceStep(Math.ceil(maxValue / Y_TICK_COUNT))
  const yMax = tickStep * Y_TICK_COUNT

  const xAt = (index: number) =>
    MARGIN.left + (lastIndex <= 0 ? plotWidth / 2 : (index / lastIndex) * plotWidth)
  const yAt = (value: number) => MARGIN.top + plotHeight - (value / yMax) * plotHeight

  const createdPath = toPath(daily, (day) => day.created, xAt, yAt)
  const completedPath = toPath(daily, (day) => day.completed, xAt, yAt)
  const createdArea =
    daily.length > 0
      ? `M${xAt(0)},${MARGIN.top + plotHeight} L${daily
          .map((day, index) => `${xAt(index)},${yAt(day.created)}`)
          .join(' L')} L${xAt(lastIndex)},${MARGIN.top + plotHeight} Z`
      : ''

  const isEmpty = daily.every((day) => day.created === 0 && day.completed === 0)
  const activeDay = active === null ? null : daily[active]
  const tooltipLeft = active === null ? 0 : Math.min(Math.max(xAt(active), 72), width - 72)

  const moveActive = (next: number) => {
    if (daily.length === 0) {
      return
    }
    setActive(Math.min(lastIndex, Math.max(0, next)))
  }

  return (
    <div ref={wrapRef} className={styles.chartWrap}>
      <svg
        width={width}
        height={height}
        className={styles.trendSvg}
        role="img"
        tabIndex={0}
        aria-label={`工单趋势：新增与完成。共 ${daily.length} 天，使用左右方向键查看每天的数据。`}
        onFocus={() => {
          if (active === null) {
            moveActive(lastIndex)
          }
        }}
        onBlur={() => setActive(null)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowLeft') {
            event.preventDefault()
            moveActive((active ?? lastIndex) - 1)
          } else if (event.key === 'ArrowRight') {
            event.preventDefault()
            moveActive((active ?? lastIndex) + 1)
          } else if (event.key === 'Home') {
            event.preventDefault()
            moveActive(0)
          } else if (event.key === 'End') {
            event.preventDefault()
            moveActive(lastIndex)
          } else if (event.key === 'Escape') {
            setActive(null)
          }
        }}
      >
        {/* 网格：实线细线，比表面深一档。虚线会被读成"阈值"或"预测" */}
        {Array.from({ length: Y_TICK_COUNT + 1 }, (_, index) => {
          const value = index * tickStep
          const y = yAt(value)
          return (
            <g key={`tick-${value}`}>
              <line
                x1={MARGIN.left}
                x2={width - MARGIN.right}
                y1={y}
                y2={y}
                stroke={CHART_COLORS.grid}
                strokeWidth={1}
              />
              <text
                x={MARGIN.left - 8}
                y={y + 4}
                textAnchor="end"
                fill={CHART_COLORS.axis}
                fontSize={11}
                style={{ fontVariantNumeric: 'tabular-nums' }}
              >
                {formatTick(value, tickStep)}
              </text>
            </g>
          )
        })}

        {daily.length > 0 && (
          <>
            {/* 只给"新增"铺一层 10% 的淡色：两条都铺会互相压暗，反而看不清 */}
            <path d={createdArea} fill={CHART_COLORS.blue} opacity={0.1} />
            {active === null && !isEmpty && (
              <>
                <circle
                  cx={xAt(lastIndex)}
                  cy={yAt(daily[lastIndex]?.created ?? 0)}
                  r={4}
                  fill={CHART_COLORS.blue}
                  stroke={CHART_COLORS.surface}
                  strokeWidth={2}
                />
                <circle
                  cx={xAt(lastIndex)}
                  cy={yAt(daily[lastIndex]?.completed ?? 0)}
                  r={4}
                  fill={CHART_COLORS.aqua}
                  stroke={CHART_COLORS.surface}
                  strokeWidth={2}
                />
              </>
            )}
            <path
              d={createdPath}
              fill="none"
              stroke={CHART_COLORS.blue}
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d={completedPath}
              fill="none"
              stroke={CHART_COLORS.aqua}
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </>
        )}

        {/* 横轴：7 天全标，30 天抽稀；窄屏再抽一档，保证 390px 下日期不重叠 */}
        {daily.map((day, index) => {
          const every =
            width < 480
              ? Math.max(2, Math.ceil(daily.length / 5))
              : daily.length <= 10
                ? 1
                : 5
          if (index !== 0 && index !== lastIndex && index % every !== 0) {
            return null
          }
          return (
            <text
              key={day.date}
              x={xAt(index)}
              y={height - 8}
              textAnchor={index === 0 ? 'start' : index === lastIndex ? 'end' : 'middle'}
              fill={CHART_COLORS.axis}
              fontSize={11}
              style={{ fontVariantNumeric: 'tabular-nums' }}
            >
              {day.date.slice(5)}
            </text>
          )
        })}

        {active !== null && activeDay && (
          <>
            <line
              x1={xAt(active)}
              x2={xAt(active)}
              y1={MARGIN.top}
              y2={MARGIN.top + plotHeight}
              stroke={CHART_COLORS.axis}
              strokeWidth={1}
            />
            <circle
              cx={xAt(active)}
              cy={yAt(activeDay.created)}
              r={4}
              fill={CHART_COLORS.blue}
              stroke={CHART_COLORS.surface}
              strokeWidth={2}
            />
            <circle
              cx={xAt(active)}
              cy={yAt(activeDay.completed)}
              r={4}
              fill={CHART_COLORS.aqua}
              stroke={CHART_COLORS.surface}
              strokeWidth={2}
            />
          </>
        )}

        {/* 命中区是整条绘图带，而不是每条折线：鼠标只要靠近某个日期就能读数 */}
        <rect
          x={MARGIN.left}
          y={MARGIN.top}
          width={plotWidth}
          height={plotHeight}
          fill="transparent"
          style={{ cursor: 'crosshair' }}
          onPointerMove={(event) => {
            const rect = event.currentTarget.getBoundingClientRect()
            const ratio = (event.clientX - rect.left) / rect.width
            moveActive(Math.round(ratio * lastIndex))
          }}
          onPointerLeave={() => setActive(null)}
        />

        {isEmpty && (
          <text
            x={MARGIN.left + plotWidth / 2}
            y={MARGIN.top + plotHeight / 2}
            textAnchor="middle"
            fill={CHART_COLORS.axis}
            fontSize={13}
          >
            暂无趋势数据
          </text>
        )}
      </svg>

      {activeDay && (
        <div className={styles.chartTooltip} style={{ left: tooltipLeft }} role="status">
          <div className={styles.tooltipDate}>{activeDay.date}</div>
          <div className={styles.tooltipRow}>
            <i className={styles.tooltipKey} style={{ background: CHART_COLORS.blue }} />
            <span className={styles.tooltipLabel}>新增</span>
            <strong className={styles.tooltipValue}>{activeDay.created}</strong>
          </div>
          <div className={styles.tooltipRow}>
            <i className={styles.tooltipKey} style={{ background: CHART_COLORS.aqua }} />
            <span className={styles.tooltipLabel}>已完成</span>
            <strong className={styles.tooltipValue}>{activeDay.completed}</strong>
          </div>
        </div>
      )}

      {/* 两条及以上的线必须有图例：颜色不能是唯一的身份通道 */}
      <div className={styles.legend}>
        <span className={styles.legendItem}>
          <i className={styles.legendLine} style={{ background: CHART_COLORS.blue }} />
          新增工单
        </span>
        <span className={styles.legendItem}>
          <i className={styles.legendLine} style={{ background: CHART_COLORS.aqua }} />
          已完成
        </span>
      </div>
    </div>
  )
}

function toPath(
  daily: DailyWorkOrderCount[],
  pick: (day: DailyWorkOrderCount) => number,
  xAt: (index: number) => number,
  yAt: (value: number) => number,
): string {
  if (daily.length === 0) {
    return ''
  }
  return daily
    .map((day, index) => `${index === 0 ? 'M' : 'L'}${xAt(index)},${yAt(pick(day))}`)
    .join(' ')
}

/**
 * 把纵轴上限取成"好看"的刻度：1 / 2 / 2.5 / 5 × 10 的幂。
 *
 * 直接用 max/4 会让轴出现 21.5 这种刻度；而一律向上取整到 10 的幂，
 * 8 张单子的图会被拉到 0–10，只剩下半屏高度。
 */
function niceStep(raw: number): number {
  if (raw <= 1) {
    return 1
  }
  const exponent = Math.floor(Math.log10(raw))
  const base = Math.pow(10, exponent)
  const normalized = raw / base
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10
  return factor * base
}

function formatTick(value: number, step: number): string {
  // 步长 2.5 时，刻度是 0 / 2.5 / 5 / 7.5 / 10 —— 一律取整会显示成 0 / 3 / 5 / 8 / 10
  if (!Number.isInteger(step)) {
    return value.toFixed(1).replace(/\.0$/, '')
  }
  return String(Math.round(value))
}
