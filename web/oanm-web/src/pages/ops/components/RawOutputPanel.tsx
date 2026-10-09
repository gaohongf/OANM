import { Collapse, Empty, Typography, theme } from 'antd'

const { Text } = Typography

/**
 * 模型的原始输出（一行一条，未经解析）。
 *
 * ## 为什么必须有这个面板
 * 页面上的卡片和表单字段都是**解析成功**之后的产物。模型不守 JSONL 格式时，
 * 用户看到的是"转了圈但一张卡片都没出来"—— 没有这个面板就只能盯着空页面猜，
 * 而猜不出到底是网络断了、模型跑偏了、还是解析代码有 bug。
 * 把它摊开，问题一眼可定位。
 *
 * ## 为什么是折叠的
 * 正常情况下它就是噪音：用户关心的是卡片，不是我们和模型之间的报文。
 */
export function RawOutputPanel({ lines }: { lines: string[] }) {
  const { token } = theme.useToken()

  return (
    <Collapse
      ghost
      items={[
        {
          key: 'raw',
          label: <Text type="secondary">原始输出（{lines.length} 行）</Text>,
          children:
            lines.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有收到任何输出" />
            ) : (
              <pre
                style={{
                  margin: 0,
                  maxHeight: 240,
                  overflow: 'auto',
                  padding: token.paddingSM,
                  background: token.colorFillQuaternary,
                  borderRadius: token.borderRadius,
                  fontSize: token.fontSizeSM,
                  // 报文里可能有很长的行，允许横向滚动而不是撑破布局
                  whiteSpace: 'pre',
                }}
              >
                {lines.join('\n')}
              </pre>
            ),
        },
      ]}
    />
  )
}
