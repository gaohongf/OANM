import { CHART_COLORS } from '../palette'
import styles from '../Dashboard.module.css'

interface StatusDonutProps {
  pending: number
  accepted: number
  resolved: number
  closed: number
}

/** 环形图尺寸。半径和线宽写死，卡片宽度变化时靠 flex 居中而不是缩放 */
const SIZE = 140
const RADIUS = 50
const STROKE = 14
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/**
 * 工单状态分布。
 *
 * ## 色块之间留的是"表面色缝隙"，不是描边
 * 每段弧线两端各让出 1px（合计 2px），缝隙露出的是卡片白底。给弧线描边会在
 * 数据之外多出一圈线宽，看起来像第二个数值。
 *
 * ## 图例带数量与占比
 * 青绿、黄色与白底的对比度低于 3:1，颜色本身不足以承担"这是哪一段"。
 * 所以图例同时给出文字、数量和百分比 —— 不看颜色也能读完整张图。
 */
export function StatusDonut({ pending, accepted, resolved, closed }: StatusDonutProps) {
  const segments = [
    { key: 'pending', label: '待受理', value: pending, color: CHART_COLORS.blue },
    { key: 'accepted', label: '已受理', value: accepted, color: CHART_COLORS.orange },
    { key: 'resolved', label: '已解决', value: resolved, color: CHART_COLORS.aqua },
    { key: 'closed', label: '已关闭', value: closed, color: CHART_COLORS.neutral },
  ]
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)
  const completed = resolved + closed
  const completionRate = total > 0 ? Math.round((completed / total) * 100) : 0

  let offset = 0
  const arcs = segments.map((segment) => {
    const length = total > 0 ? (segment.value / total) * CIRCUMFERENCE : 0
    // 太短的分段留缝隙反而会把它抹掉；只在画得下的时候留
    const gap = length > 4 ? 2 : 0
    const arc = { ...segment, length, gap, offset }
    offset += length
    return arc
  })

  return (
    <div className={styles.donutWrap}>
      <div className={styles.donutMain}>
        <svg
          width={SIZE}
          height={SIZE}
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          className={styles.donutSvg}
          role="img"
          aria-label={`工单状态分布：待受理 ${pending} 张、已受理 ${accepted} 张、已解决 ${resolved} 张、已关闭 ${closed} 张，共 ${total} 张`}
        >
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke="#eef2f7"
          strokeWidth={STROKE}
        />
        {arcs.map((arc) => {
          if (arc.length <= 0) {
            return null
          }
          return (
            <circle
              key={arc.key}
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke={arc.color}
              strokeWidth={STROKE}
              strokeLinecap="butt"
              strokeDasharray={`${Math.max(0, arc.length - arc.gap)} ${
                CIRCUMFERENCE - Math.max(0, arc.length - arc.gap)
              }`}
              strokeDashoffset={-arc.offset}
              transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
            />
          )
        })}
        <text
          x={SIZE / 2}
          y={SIZE / 2 - 2}
          textAnchor="middle"
          className={styles.donutValue}
        >
          {total}
        </text>
        <text
          x={SIZE / 2}
          y={SIZE / 2 + 18}
          textAnchor="middle"
          className={styles.donutCaption}
        >
          工单总数
        </text>
        </svg>

        <div className={styles.donutLegend}>
          {segments.map((segment) => (
            <div
              key={segment.key}
              className={`${styles.legendRow} ${
                segment.value === 0 ? styles.legendRowMuted : ''
              }`}
            >
              <i className={styles.dot} style={{ background: segment.color }} />
              <span className={styles.legendLabel}>{segment.label}</span>
              <span className={styles.legendValue}>{segment.value}</span>
              <span className={styles.legendPercent}>
                {total > 0 ? Math.round((segment.value / total) * 100) : 0}%
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className={styles.rate}>
        <div className={styles.rateRow}>
          <span>工单完成率</span>
          <strong>{completionRate}%</strong>
        </div>
        <div
          className={styles.meter}
          role="meter"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={completionRate}
          aria-label="工单完成率"
        >
          <div
            className={styles.meterFill}
            style={{ width: `${completionRate}%`, background: CHART_COLORS.aqua }}
          />
        </div>
      </div>
    </div>
  )
}
