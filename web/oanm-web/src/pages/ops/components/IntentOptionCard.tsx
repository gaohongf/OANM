import { Card, Flex, Progress, Tag, theme, Typography } from 'antd'

import { decisionActionOf } from '../../../api/ai'
import type { IntentOption } from '../../../api/ai'

const { Text } = Typography

interface IntentOptionCardProps {
  option: IntentOption
  selected: boolean
  /** 流还没结束（或者正在开新的一轮）时不允许选中 */
  disabled?: boolean
  /**
   * 不是"从几个猜测里挑一个"的卡片（故障确认的"是哪种现象"也走这里）：
   * 不显示编号、概率和概率条。
   */
  plain?: boolean
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
 * ## 为什么压成三行、内容只留一行
 * 一组通常有 3-5 张卡，摊在对话里，每张多一行就是一屏。所以标题、编号、概率并成
 * 一行，具体内容单行省略（悬停看全文），概率条细成一条底线。
 *
 * ## 选中态为什么不只用背景色
 * 只用背景色的话，"选中"和"hover"看起来会很像。这里用主色描边 + 加粗左边框，
 * 和 hover 的区别在形状上而不是明度上。
 */
export function IntentOptionCard({
  option,
  selected,
  disabled = false,
  plain = false,
  onSelect,
}: IntentOptionCardProps) {
  const { token } = theme.useToken()
  // 决策卡片（「已经解决了」/「帮我提工单」）和故障确认卡片都不是从几个猜测里挑一个，
  // 所以不显示编号、概率和概率条 —— 那些东西会让它看起来像又一个意图选项。
  const simple = plain || decisionActionOf(option) !== null

  return (
    <Card
      size="small"
      hoverable={!disabled}
      // 卡片逐条出现时用它做入场动画（index.css 里唯一的全局类，且尊重 prefers-reduced-motion）
      className="page-transition"
      styles={{ body: { padding: simple ? '12px 14px' : '10px 12px' } }}
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
      <Flex vertical gap={4}>
        <Flex align="center" gap={8}>
          {!simple && (
            // 编号就是提交时带走的 userSelect，摆出来免得用户对不上
            <Tag color={selected ? 'blue' : undefined} style={{ marginInlineEnd: 0 }}>
              #{option.id}
            </Tag>
          )}
          <Text strong ellipsis style={{ flex: 1, minWidth: 0 }}>
            {option.title}
          </Text>
          {!simple && (
            <Text type="secondary" style={{ fontSize: token.fontSizeSM, whiteSpace: 'nowrap' }}>
              {option.probability}%
            </Text>
          )}
        </Flex>
        {option.content !== '' &&
          (simple ? (
            <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              {option.content}
            </Text>
          ) : (
            // 单行省略：一组卡片要能一屏扫完，完整内容在悬停的浮层里
            <Text
              type="secondary"
              ellipsis={{ tooltip: option.content }}
              style={{ fontSize: token.fontSizeSM }}
            >
              {option.content}
            </Text>
          ))}
        {!simple && (
          <Progress
            percent={option.probability}
            size="small"
            showInfo={false}
            style={{ marginBottom: 0 }}
          />
        )}
      </Flex>
    </Card>
  )
}
