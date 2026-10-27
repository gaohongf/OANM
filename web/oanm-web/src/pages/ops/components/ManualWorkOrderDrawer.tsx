import { useState } from 'react'
import { App as AntdApp, Button, Drawer, Flex, Form, Input, Select, Typography } from 'antd'

import {
  createWorkOrder,
  PRIORITY_OPTIONS,
  TYPE_OPTIONS,
  WORK_ORDER_PERMISSIONS,
} from '../../../api/workOrder'
import type { WorkOrderPriority, WorkOrderType } from '../../../api/workOrder'
import { Auth } from '../../../permission/Auth'

const { Text } = Typography
const { TextArea } = Input

// Select 只认 {value,label}，元数据里还带着颜色，这里摘一下
const TYPE_SELECT_OPTIONS = TYPE_OPTIONS.map((item) => ({ value: item.value, label: item.label }))
const PRIORITY_SELECT_OPTIONS = PRIORITY_OPTIONS.map((item) => ({
  value: item.value,
  label: item.label,
}))

export interface ManualWorkOrderValues {
  title: string
  type: WorkOrderType
  priority: WorkOrderPriority
  problemDescription: string
  originalProblemDescription?: string
  solutionDetail?: string
}

interface ManualWorkOrderDrawerProps {
  open: boolean
  onClose: () => void
  /** 建单成功，带回新工单 id */
  onCreated: (id: string) => void
}

/**
 * 手动建单抽屉。
 *
 * 不经过 AI：没有配置 AI key、或者不想等模型时，从这里逐项填写后直接提交。
 * 字段与智能提单的确认抽屉一致，区别只是初始值来自用户而不是模型。
 *
 * ## 选填字段提交前归一化
 * 只输入空格等同于没填，转成 `undefined` 再发出去，避免在详情里留下一段看不见内容的
 * 「原始描述」或「解决方法」。
 */
export function ManualWorkOrderDrawer({ open, onClose, onCreated }: ManualWorkOrderDrawerProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<ManualWorkOrderValues>()
  const [submitting, setSubmitting] = useState(false)

  const handleClose = () => {
    if (!submitting) {
      onClose()
    }
  }

  const handleSubmit = () => {
    void form
      .validateFields()
      .then(async (values) => {
        setSubmitting(true)
        try {
          const { id } = await createWorkOrder({
            title: values.title.trim(),
            type: values.type,
            priority: values.priority,
            problemDescription: values.problemDescription.trim(),
            originalProblemDescription: values.originalProblemDescription?.trim() || undefined,
            solutionDetail: values.solutionDetail?.trim() || undefined,
          })
          message.success('工单已提交')
          onCreated(id)
          onClose()
        } catch (error) {
          message.error(error instanceof Error ? error.message : '提交失败')
        } finally {
          setSubmitting(false)
        }
      })
      .catch(() => undefined)
  }

  return (
    <Drawer
      title="手动填写工单"
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
      <Form<ManualWorkOrderValues> form={form} layout="vertical" preserve={false}>
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
          <TextArea
            rows={5}
            maxLength={2000}
            showCount
            placeholder="问题是什么、影响范围、复现步骤"
          />
        </Form.Item>

        <Form.Item
          name="originalProblemDescription"
          label="原始描述"
          rules={[{ max: 16000, message: '最长 16000 个字符' }]}
          extra="选填。一行一轮，作为问题最初描述的归档。"
        >
          <TextArea rows={3} maxLength={16000} placeholder="问题最初是怎么描述的" />
        </Form.Item>

        <Form.Item
          name="solutionDetail"
          label="解决方法"
          rules={[{ max: 16000, message: '最长 16000 个字符' }]}
          extra="选填。可以留空，等受理人处理时再补充。"
        >
          <TextArea rows={6} maxLength={16000} placeholder="建议的处理步骤" />
        </Form.Item>
      </Form>
    </Drawer>
  )
}
