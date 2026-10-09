import { Card, Progress, Space, Tag, theme, Typography } from 'antd'

import type { IntentOption } from '../../../api/ai'

const { Text } = Typography

interface IntentOptionCardProps {
  option: IntentOption
  selected: boolean
  /** 流还没结束（或者正在开新的一轮）时不允许选中 */
  disabled?: boolean
  onSelect: (option: IntentOption) => void
}

/**
 * 一张意图选项卡片。
 *
 * ## 为什么整张卡都能点
 * 这是在"挑一个最像的"场景下用的 —— 用户看的是标题和内容，不是按钮。
 * 让整块可点比放一个"选择"按钮少一次瞄准。
 *
 * ## 概率为什么用 Progress 而不是只写数字
 * 这几个选项之间是**相对**关系（80 / 50 / 10），横向长度比孤立的一个百分数
 * 更容易一眼比较出来。
 *
 * ## 选中态为什么不只用背景色
 * 只用背景色的话，"选中"和"hover"看起来会很像。这里用主色描边 + 加粗左边框，
 * 和 hover 的区别在形状上而不是明度上。
 */
export function IntentOptionCard({ option, selected, disabled = false, onSelect }: IntentOptionCardProps) {
  const { token } = theme.useToken()

  return (
    <Card
      size="small"
      hoverable={!disabled}
      // 卡片逐条出现时用它做入场动画（index.css 里唯一的全局类，且尊重 prefers-reduced-motion）
      className="page-transition"
      style={{
        borderColor: selected ? token.colorPrimary : undefined,
        borderInlineStartWidth: selected ? 4 : undefined,
        borderInlineStartColor: selected ? token.colorPrimary : undefined,
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.6 : 1,
      }}
      onClick={() => {
        if (!disabled) {
          onSelect(option)
        }
      }}
    >
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Space size={8} wrap>
          {/* 编号就是提交时带走的 userSelect，摆出来免得用户对不上 */}
          <Tag color={selected ? 'blue' : undefined}>#{option.id}</Tag>
          <Text strong>{option.title}</Text>
        </Space>
        {option.content !== '' && <Text type="secondary">{option.content}</Text>}
        <Space size={8} style={{ width: '100%' }}>
          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            可能性
          </Text>
          <Progress
            percent={option.probability}
            size="small"
            showInfo
            style={{ flex: 1, marginBottom: 0, minWidth: 120 }}
          />
        </Space>
      </Space>
    </Card>
  )
}
