import { useEffect, useState } from 'react'
import { App as AntdApp, Form, Input, Modal, Select, Switch } from 'antd'

import { createSoftware, updateSoftware } from '../../../api/software'
import type {
  SaveSoftwarePayload,
  SoftwareCategory,
  SoftwareManageRow,
} from '../../../api/software'

interface SoftwareFormValues {
  name: string
  vendor?: string
  categoryId: string
  description?: string
  enabled: boolean
}

interface SoftwareFormModalProps {
  open: boolean
  /** null = 新建 */
  editing: SoftwareManageRow | null
  categories: SoftwareCategory[]
  onCancel: () => void
  onSuccess: () => void
}

/**
 * 新建 / 编辑软件。
 *
 * ## 启用状态也放在表单里
 * 新建时默认启用；编辑时它会作为普通字段提交。列表上的快速开关走另一个端点
 * （`PUT /api/software/{id}/enabled`），两者不冲突：这里提交的是整张表单，
 * 本来就要带上完整字段。
 */
export function SoftwareFormModal({
  open,
  editing,
  categories,
  onCancel,
  onSuccess,
}: SoftwareFormModalProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<SoftwareFormValues>()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    form.setFieldsValue(
      editing
        ? {
            name: editing.name,
            vendor: editing.vendor ?? undefined,
            categoryId: editing.categoryId,
            description: editing.description ?? undefined,
            enabled: editing.enabled,
          }
        : {
            name: '',
            vendor: '',
            categoryId: undefined,
            description: '',
            enabled: true,
          },
    )
  }, [open, editing, form])

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const payload: SaveSoftwarePayload = {
        name: values.name.trim(),
        vendor: values.vendor?.trim() || undefined,
        categoryId: values.categoryId,
        description: values.description?.trim() || undefined,
        enabled: values.enabled,
      }
      if (editing) {
        await updateSoftware(editing.id, payload)
        message.success('修改成功')
      } else {
        await createSoftware(payload)
        message.success('创建成功')
      }
      onSuccess()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={editing ? '编辑软件' : '新建软件'}
      open={open}
      onOk={() => void onSubmit()}
      confirmLoading={submitting}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="name"
          label="软件名称"
          rules={[{ required: true, message: '请输入软件名称' }, { max: 200, message: '最长 200 个字符' }]}
        >
          <Input placeholder="例如：惠普 LaserJet 打印机驱动" />
        </Form.Item>
        <Form.Item
          name="vendor"
          label="厂商 / 发布方"
          rules={[{ max: 100, message: '最长 100 个字符' }]}
        >
          <Input placeholder="例如：HP" />
        </Form.Item>
        <Form.Item
          name="categoryId"
          label="分类"
          rules={[{ required: true, message: '请选择分类' }]}
        >
          <Select
            placeholder="选择分类"
            options={categories.map((category) => ({
              value: category.id,
              label: category.name,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="description"
          label="简介"
          rules={[{ max: 2000, message: '最长 2000 个字符' }]}
        >
          <Input.TextArea rows={4} placeholder="这个软件是做什么的、适用哪些设备" />
        </Form.Item>
        <Form.Item name="enabled" label="上架" valuePropName="checked">
          <Switch checkedChildren="启用" unCheckedChildren="停用" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
