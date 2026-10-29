import { Alert, Button, Card, Flex, Skeleton, Space, Tag, Typography, theme } from 'antd'
import { CheckCircleFilled, RightOutlined } from '@ant-design/icons'

import type { WorkOrderDraft } from '../../../api/ai'
import {
  findOption,
  normalizePriority,
  normalizeType,
  PRIORITY_OPTIONS,
  TYPE_OPTIONS,
} from '../../../api/workOrder'

const { Text } = Typography

export interface WorkOrderDraftCardProps {
  status: 'generating' | 'ready' | 'failed' | 'submitted'
  draft: WorkOrderDraft | null
  /** true = 自助解决记录：提交后直接是「已解决」，受理人是本人 */
  selfResolved: boolean
  workOrderId: string | null
  error: string | null
  /** 打开确认/修改抽屉 */
  onOpen: () => void
  /** 跳到工单详情 */
  onView: () => void
}

/**
 * 对话里的工单气泡。
 *
 * 四个状态共用同一个位置，用户看到的是一条会变化的回答：
 * 骨架（正在生成）→ 卡片（可点开确认）→ 已创建（可去看工单）。
 * 失败时留在原地说明原因，而不是让这条气泡消失 —— 消失会让人以为没点成功。
 */
export function WorkOrderDraftCard({
  status,
  draft,
  selfResolved,
  workOrderId,
  error,
  onOpen,
  onView,
}: WorkOrderDraftCardProps) {
  const { token } = theme.useToken()

  if (status === 'generating') {
    return (
      <div style={{ width: '100%' }}>
        <Skeleton active title={false} paragraph={{ rows: 3 }} />
        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          正在生成工单…
        </Text>
      </div>
    )
  }

  if (status === 'failed') {
    return (
      <Alert
        type="warning"
        showIcon
        message="工单生成失败"
        description={
          <>
            {error ?? '模型没有返回工单内容。'}
            <br />
            请点下面的「生成工单」重新试一次。
          </>
        }
      />
    )
  }

  if (status === 'submitted') {
    return (
      <Card size="small" style={{ borderColor: token.colorSuccessBorder }}>
        <Flex gap={12} align="center">
          <CheckCircleFilled
            style={{ color: token.colorSuccess, fontSize: token.fontSizeHeading3, flexShrink: 0 }}
          />
          <div style={{ flex: 1, minWidth: 0 }}>
            <Text strong>{selfResolved ? '自助解决记录已创建' : '工单已创建'}</Text>
            <div style={{ marginTop: 2 }}>
              <Text type="secondary">工单号：</Text>
              {workOrderId !== null ? (
                <Text code copyable={{ text: workOrderId }}>
                  {workOrderId}
                </Text>
              ) : (
                <Text type="secondary">-</Text>
              )}
            </div>
          </div>
          <Button type="link" onClick={onView} style={{ flexShrink: 0 }}>
            {selfResolved ? '查看记录' : '查看工单'}
          </Button>
        </Flex>
      </Card>
    )
  }

  // ready 但没有草稿：形状上不该出现（`readWorkOrderDraft` 会把它判失败），
  // 真遇到了也不能让气泡变成一块空白
  if (draft === null) {
    return <Alert type="warning" showIcon message="工单内容为空，请重新生成" />
  }

  return (
    <Card size="small" style={{ borderColor: token.colorBorderSecondary }}>
      <Flex gap={12} align="flex-start">
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text strong>{draft.title}</Text>
          <Space size={6} wrap style={{ marginTop: 6, display: 'flex' }}>
            {selfResolved && <Tag color="cyan">自助解决</Tag>}
            {draft.imageUrls.length > 0 && (
              <Tag color="geekblue">现场图片 {draft.imageUrls.length}</Tag>
            )}
            <DraftTags draft={draft} />
          </Space>
          <Typography.Paragraph
            type="secondary"
            ellipsis={{ rows: 2 }}
            style={{ margin: '6px 0 0', marginBottom: 0 }}
          >
            {draft.problemDescription}
          </Typography.Paragraph>
        </div>
        {/* 明确的动作区：点卡片本身不理解成"打开"，避免误触 */}
        <Button type="text" onClick={onOpen} style={{ flexShrink: 0 }}>
          查看并提交 <RightOutlined />
        </Button>
      </Flex>
    </Card>
  )
}

/** 类型与优先级。归一化不了就如实显示模型给的原值，不伪装成某一个枚举。 */
function DraftTags({ draft }: { draft: WorkOrderDraft }) {
  const typeMeta = findOption(TYPE_OPTIONS, normalizeType(draft.type))
  const priorityMeta = findOption(PRIORITY_OPTIONS, normalizePriority(draft.priority))

  return (
    <>
      {typeMeta !== undefined ? (
        <Tag color={typeMeta.color}>{typeMeta.label}</Tag>
      ) : draft.type !== '' ? (
        <Tag>{draft.type}</Tag>
      ) : null}
      {priorityMeta !== undefined ? (
        <Tag color={priorityMeta.color}>{priorityMeta.label}</Tag>
      ) : draft.priority !== '' ? (
        <Tag>{draft.priority}</Tag>
      ) : null}
    </>
  )
}
