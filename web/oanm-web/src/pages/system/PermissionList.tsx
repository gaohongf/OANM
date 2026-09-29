import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, Tooltip } from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'

import { request } from '../../api/http'
import { Auth } from '../../permission/Auth'

interface Permission {
  id: number
  permissionKey: string
  label: string
  /** 内置的超管权限（键为 *），不允许改名或删除。由后端计算，不是存出来的字段。 */
  builtIn: boolean
}

interface PermissionPage {
  total: number
  current: number
  size: number
  records: Permission[]
}

interface PermissionForm {
  permissionKey: string
  label: string
}

const PERM_CREATE = 'POST:/api/auth/permissions'
const PERM_UPDATE = 'PUT:/api/auth/permissions/{id}'
const PERM_DELETE = 'DELETE:/api/auth/permissions/{id}'

/**
 * 权限管理。
 *
 * ## 必须分页
 * 权限会随端点自注册增长到"一个端点一条"，几百上千行是常态。这和角色、菜单（几十行）不同，
 * 所以只有这一页用服务端分页。
 *
 * ## 内置的超管权限
 * 键为 `*` 的那一行是种子数据，后端的接口层拒绝改名和删除（防止"能管权限的人造一个 `*`
 * 授给自己"这条提权路径）。这一页如实把它标出来并禁用按钮，而不是让用户点了才发现被拒。
 */
export default function PermissionList() {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<Permission[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Permission | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<PermissionForm>()

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const page = await request<PermissionPage>('/api/auth/permissions', {
          // 空字符串不要发过去 —— 后端会当成一个真的搜索词
          query: { current, size, keyword: keyword.trim() || undefined },
        })
        setRecords(page.records)
        setTotal(page.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, message],
  )

  useEffect(() => {
    // 理由同其他列表页：挂载时用 silent，避免在 effect 里同步 setState
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
    form.setFieldsValue({ permissionKey: '', label: '' })
  }

  const openEdit = (record: Permission) => {
    setEditing(record)
    setModalOpen(true)
    form.setFieldsValue({ permissionKey: record.permissionKey, label: record.label })
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      if (editing) {
        await request<void>(`/api/auth/permissions/${editing.id}`, { method: 'PUT', body: values })
        message.success('修改成功')
      } else {
        await request<void>('/api/auth/permissions', { method: 'POST', body: values })
        message.success('创建成功')
      }
      setModalOpen(false)
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  const onDelete = async (id: number) => {
    try {
      await request<void>(`/api/auth/permissions/${id}`, { method: 'DELETE' })
      message.success('删除成功')
      await load()
    } catch (error) {
      // 被菜单引用时后端会拒绝（409），并说明要先解除引用。如实展示它的提示。
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  const onSearch = (value: string) => {
    setKeyword(value)
    // 换了搜索词要回到第 1 页 —— 否则可能停在一个"总数为 0 的第 5 页"上
    setCurrent(1)
  }

  return (
    <Card
      title="权限管理"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="按权限标识或标签搜索"
            enterButton={<SearchOutlined />}
            onSearch={onSearch}
            style={{ width: 280 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Auth code={PERM_CREATE}>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建权限
            </Button>
          </Auth>
        </Space>
      }
    >
      <Table<Permission>
        rowKey="id"
        loading={loading}
        dataSource={records}
        scroll={{ x: 'max-content' }}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, pageSize) => {
            setCurrent(page)
            setSize(pageSize)
          },
        }}
        columns={[
          {
            title: '权限标识',
            dataIndex: 'permissionKey',
            render: (key: string, record) => (
              <Space size={4}>
                {/* 权限标识直接给前端用（按钮级权限判断），所以不加掩码 */}
                <Tag color={record.builtIn ? 'gold' : undefined} style={{ fontFamily: 'monospace' }}>
                  {key}
                </Tag>
              </Space>
            ),
          },
          { title: '标签', dataIndex: 'label' },
          {
            title: '类型',
            width: 100,
            render: (_, record) =>
              record.builtIn ? (
                <Tooltip title="内置的超级管理员权限，匹配一切。后端拒绝改名和删除，防止提权。">
                  <Tag color="gold">内置超管</Tag>
                </Tooltip>
              ) : (
                <Tag>普通</Tag>
              ),
          },
          {
            title: '操作',
            width: 160,
            render: (_, record) =>
              record.builtIn ? (
                <Tooltip title="内置权限不允许修改或删除">
                  <span style={{ color: 'rgba(0,0,0,0.25)' }}>不可操作</span>
                </Tooltip>
              ) : (
                <Space>
                  <Auth code={PERM_UPDATE}>
                    <Button type="link" size="small" onClick={() => openEdit(record)}>
                      编辑
                    </Button>
                  </Auth>
                  <Auth code={PERM_DELETE}>
                    <Popconfirm
                      title={`确定删除权限 ${record.permissionKey}？`}
                      description="被菜单引用的权限后端会拒绝删除，需要先解除引用。"
                      onConfirm={() => onDelete(record.id)}
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
        title={editing ? '编辑权限' : '新建权限'}
        open={modalOpen}
        onOk={onSubmit}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="permissionKey"
            label="权限标识"
            extra="要与接口的 方法:路径模式 完全一致，例如 GET:/api/ops/work_order/{id}，否则授权了也不会生效。"
            rules={[
              { required: true, message: '请输入权限标识' },
              { max: 255, message: '最长 255 个字符' },
            ]}
          >
            <Input placeholder="GET:/api/ops/work_order/{id}" />
          </Form.Item>
          <Form.Item
            name="label"
            label="标签"
            extra="给人看的可读名字。自注册产生的权限默认用标识本身占位，建议改成「查看工单」这类描述。"
            rules={[
              { required: true, message: '请输入标签' },
              { max: 64, message: '最长 64 个字符' },
            ]}
          >
            <Input placeholder="查看工单" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
