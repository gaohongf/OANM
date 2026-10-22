import type { ReactNode } from 'react'
import { Card } from 'antd'

import styles from '../Dashboard.module.css'

interface KpiCardProps {
  icon: ReactNode
  label: string
  value: ReactNode
  unit: string
  /** 数值下方的一句说明：趋势、构成或一个可点的动作 */
  hint: ReactNode
  /** 右侧的迷你走势 / 构成条。没有可用数据时留空，不用装饰填位 */
  visual?: ReactNode
  /** 图标的强调色，同时用作迷你走势的颜色 */
  accent: string
}

/**
 * 概览的指标卡。
 *
 * 数值用比例数字（默认字体），不加 `tabular-nums`：等宽数字是为"上下对齐的一列"
 * 准备的，放在 30px 的独立大数字上会让 121 看起来松垮。对齐需求在数据表里。
 */
export function KpiCard({ icon, label, value, unit, hint, visual, accent }: KpiCardProps) {
  return (
    <Card className={styles.kpiCard} styles={{ body: { padding: 14 } }}>
      <div className={styles.kpiTop}>
        <div style={{ minWidth: 0 }}>
          <div className={styles.kpiLabel}>{label}</div>
          <div className={styles.kpiValue}>
            {value}
            <span className={styles.kpiUnit}>{unit}</span>
          </div>
        </div>
        <span
          className={styles.kpiIcon}
          style={{ color: accent, background: `${accent}14` }}
          aria-hidden="true"
        >
          {icon}
        </span>
      </div>
      <div className={styles.kpiBottom}>
        <div className={styles.kpiHint}>{hint}</div>
        {visual && <div className={styles.kpiVisual}>{visual}</div>}
      </div>
    </Card>
  )
}
