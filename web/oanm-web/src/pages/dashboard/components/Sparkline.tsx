interface SparklineProps {
  /** 按时间升序的数值。至少两个点才画得出趋势 */
  values: number[]
  color: string
  width?: number
  height?: number
}

/**
 * 指标卡里的迷你走势。
 *
 * 刻意没有坐标轴、没有 Tooltip、也没有每个点的数值：它只回答"最近在涨还是在跌"，
 * 精确值就在它左边的那个大数字里。给它加交互反而会让一张卡片里出现两个焦点。
 *
 * 用 `aria-hidden` 是因为它对读屏用户没有信息量 —— 同一张卡片上的数值和说明
 * 已经完整表达了这件事。
 */
export function Sparkline({ values, color, width = 96, height = 32 }: SparklineProps) {
  if (values.length < 2) {
    return null
  }

  const max = Math.max(1, ...values)
  // 四周各留 6px：末端圆点是半径 4 + 2px 描边，贴边会被 SVG 视口裁掉一半
  const inset = 6
  const stepX = (width - inset * 2) / (values.length - 1)
  const top = 6
  const bottom = height - 6
  const points = values.map((value, index) => {
    const x = inset + index * stepX
    const y = bottom - (value / max) * (bottom - top)
    return { x, y }
  })

  const line = points.map((point) => `${point.x},${point.y}`).join(' ')
  const area = `M${inset},${height} L${points
    .map((point) => `${point.x},${point.y}`)
    .join(' L')} L${width - inset},${height} Z`
  const last = points[points.length - 1]

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      aria-hidden="true"
      style={{ display: 'block', maxWidth: '100%', height: 'auto' }}
    >
      <path d={area} fill={color} opacity={0.1} />
      <polyline
        points={line}
        fill="none"
        stroke={color}
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* 2px 表面色描边：即使线从点下方穿过，圆点也仍然可读 */}
      <circle cx={last.x} cy={last.y} r={4} fill={color} stroke="#ffffff" strokeWidth={2} />
    </svg>
  )
}
