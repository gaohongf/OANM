import { Button, Drawer, Flex, Form, Input, Select, Typography } from 'antd'

import type { WorkOrderDraft } from '../../../api/ai'
import {
  normalizePriority,
  normalizeType,
  PRIORITY_OPTIONS,
  TYPE_OPTIONS,
  WORK_ORDER_PERMISSIONS,
} from '../../../api/workOrder'
import type { WorkOrderPriority, WorkOrderType } from '../../../api/workOrder'
import { Auth } from '../../../permission/Auth'

const { Text } = Typography
const { TextArea } = Input

// Select 只认 {value,label}，我们自己那份元数据还带着颜色，这里摘一下
const TYPE_SELECT_OPTIONS = TYPE_OPTIONS.map((item) => ({ value: item.value, label: item.label }))
const PRIORITY_SELECT_OPTIONS = PRIORITY_OPTIONS.map((item) => ({
  value: item.value,
  label: item.label,
}))

export interface WorkOrderFormValues {
  title: string
  type: WorkOrderType
  priority: WorkOrderPriority
  problemDescription: string
  solutionDetail?: string
}

export interface WorkOrderDraftDrawerProps {
  open: boolean
  /** 模型生成、并被用户改过的工单；打开时用它预填 */
  draft: WorkOrderDraft | null
  /** 用户原话，一行一轮；只读展示，不参与表单提交 */
  originalDescription: string
  submitting: boolean
  onClose: () => void
  /** 每次改动都回写，关掉抽屉再打开时改动还在 */
  onChange: (draft: WorkOrderDraft) => void
  onSubmit: (values: WorkOrderFormValues) => void
}

/**
 * 工单确认抽屉：模型填好的字段在这里可以逐项修改，确认后才真正建单。
 *
 * ## 为什么改动用 `onValuesChange` 往回流，而不是等提交
 * 用户很容易"改一半先关掉看看对话"。等提交才保存的话，再打开时又变回模型的原始值，
 * 改过的东西白改了。所以每次改动都回写到父组件的草稿里（它同时落 sessionStorage）。
 *
 * ## 为什么用 `destroyOnHidden` + `preserve={false}`
 * 抽屉关掉后表单就销毁，下次打开按最新的草稿重新初始化。否则 antd 会保留上一次的
 * 字段值，草稿里那些"重新生成"后的新内容就显示不出来。
 */
export function WorkOrderDraftDrawer({
  open,
  draft,
  originalDescription,
  submitting,
  onClose,
  onChange,
  onSubmit,
}: WorkOrderDraftDrawerProps) {
  const [form] = Form.useForm<WorkOrderFormValues>()

  const handleClose = () => {
    if (!submitting) {
      onClose()
    }
  }

  const handleSubmit = () => {
    void form
      .validateFields()
      .then((values) => onSubmit(values))
      .catch(() => undefined)
  }

  const initialValues: Partial<WorkOrderFormValues> = {
    title: draft?.title ?? '',
    // 归一化失败时留空：下拉框是必填的，用户必须自己选一个，而不是被塞进一个假值
    type: normalizeType(draft?.type) ?? undefined,
    priority: normalizePriority(draft?.priority) ?? undefined,
    problemDescription: draft?.problemDescription ?? '',
    solutionDetail: draft?.solutionDetail ?? '',
  }

  return (
    <Drawer
      title="确认工单"
      open={open}
      onClose={handleClose}
      width="min(720px, 100%)"
      destroyOnHidden
      closable={!submitting}
      maskClosable={!submitting}
      footer={
        <Flex justify="flex-end" gap={8}>
          <Button onClick={handleClose} disabled={submitting}>
            取消
          </Button>
          <Auth
            code={WORK_ORDER_PERMISSIONS.create}
            fallback={<Text type="secondary">没有建单权限，请联系管理员</Text>}
          >
            <Button type="primary" loading={submitting} onClick={handleSubmit}>
              提交工单
            </Button>
          </Auth>
        </Flex>
      }
    >
      <Form<WorkOrderFormValues>
        form={form}
        layout="vertical"
        initialValues={initialValues}
        preserve={false}
        onValuesChange={(_, values) => {
          onChange({
            title: values.title ?? '',
            type: values.type ?? '',
            priority: values.priority ?? '',
            problemDescription: values.problemDescription ?? '',
            solutionDetail: values.solutionDetail ?? '',
          })
        }}
      >
        <Form.Item
          name="title"
          label="标题"
          rules={[{ required: true, message: '请填写标题' }, { max: 200, message: '最长 200 个字符' }]}
        >
          <Input placeholder="一句话概括这个工单" maxLength={200} />
        </Form.Item>

        <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
          <Select options={TYPE_SELECT_OPTIONS} placeholder="需求还是故障" />
        </Form.Item>

        <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]}>
          <Select options={PRIORITY_SELECT_OPTIONS} placeholder="紧急程度" />
        </Form.Item>

        <Form.Item
          name="problemDescription"
          label="工单描述"
          rules={[
            { required: true, message: '请填写工单描述' },
            { max: 2000, message: '最长 2000 个字符' },
          ]}
        >
          <TextArea rows={5} maxLength={2000} showCount placeholder="经过整理的问题描述" />
        </Form.Item>

        {originalDescription !== '' && (
          <Form.Item
            label="原始描述"
            extra="你在对话里说过的原话，一行一轮，随工单一起归档。"
          >
            <TextArea value={originalDescription} readOnly rows={4} />
          </Form.Item>
        )}

        <Form.Item name="solutionDetail" label="解决方法">
          <TextArea rows={8} placeholder="建议的处理步骤，提交前可以再改" />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
