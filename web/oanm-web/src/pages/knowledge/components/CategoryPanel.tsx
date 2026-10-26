import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'

import {
  createKnowledgeCategory,
  deleteKnowledgeCategory,
  KNOWLEDGE_PERMISSIONS,
  updateKnowledgeCategory,
} from '../../../api/knowledge'
import type { KnowledgeCategory } from '../../../api/knowledge'
import { Auth } from '../../../permission/Auth'

interface CategoryFormValues {
  name: string
  sort: number
}

interface CategoryPanelProps {
  categories: KnowledgeCategory[]
  loading: boolean
  /** 增删改后通知外层重拉分类（文章页的筛选和表单也用同一份） */
  onChanged: () => void
}

/**
 * 知识库分类维护。
 *
 * ## 列表不分页
 * 分类是人工维护的少量数据，分页只会让它更难用。
 *
 * ## 删除失败直接展示后端提示
 * "该分类下还有文章，不能删除"是后端算出来的（含草稿），
 * 前端不重复判断 —— 两边各写一套，迟早会出现"界面说能删、后端拒绝"。
 */
export function CategoryPanel({ categories, loading, onChanged }: CategoryPanelProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<CategoryFormValues>()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<KnowledgeCategory | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!modalOpen) {
      return
    }
    form.setFieldsValue(
      editing
        ? { name: editing.name, sort: editing.sort }
        : {
            name: '',
            sort: categories.length === 0 ? 1 : Math.max(...categories.map((item) => item.sort)) + 1,
          },
    )
  }, [modalOpen, editing, categories, form])

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
  }

  const openEdit = (category: KnowledgeCategory) => {
    setEditing(category)
    setModalOpen(true)
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const payload = { name: values.name.trim(), sort: values.sort }
      if (editing) {
        await updateKnowledgeCategory(editing.id, payload)
        message.success('修改成功')
      } else {
        await createKnowledgeCategory(payload)
        message.success('创建成功')
      }
      setModalOpen(false)
      onChanged()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  const onDelete = async (category: KnowledgeCategory) => {
    try {
      await deleteKnowledgeCategory(category.id)
      message.success('删除成功')
      onChanged()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Auth code={KNOWLEDGE_PERMISSIONS.categoryCreate}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建分类
          </Button>
        </Auth>
      </Space>

      <Table<KnowledgeCategory>
        rowKey="id"
        loading={loading}
        dataSource={categories}
        pagination={false}
        columns={[
          { title: '分类名称', dataIndex: 'name' },
          { title: '已发布', dataIndex: 'publishedCount', width: 100 },
          { title: '排序', dataIndex: 'sort', width: 100 },
          {
            title: '操作',
            key: 'actions',
            width: 160,
            render: (_, record) => (
              <Space>
                <Auth code={KNOWLEDGE_PERMISSIONS.categoryUpdate}>
                  <Button type="link" size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={KNOWLEDGE_PERMISSIONS.categoryRemove}>
                  <Popconfirm
                    title={`确定删除分类 ${record.name}？`}
                    description="分类下还有文章时后端会拒绝删除。"
                    onConfirm={() => void onDelete(record)}
                  >
                    <Button type="link" danger size="small">
                      删除
                    </Button>
                  </Popconfirm>
                </Auth>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑分类' : '新建分类'}
        open={modalOpen}
        onOk={() => void onSubmit()}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="分类名称"
            rules={[
              { required: true, message: '请输入分类名称' },
              { max: 64, message: '最长 64 个字符' },
            ]}
          >
            <Input placeholder="例如：网络设备" />
          </Form.Item>
          <Form.Item
            name="sort"
            label="排序"
            extra="数值小的排在前面"
            rules={[{ required: true, message: '请填写排序值' }]}
          >
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
