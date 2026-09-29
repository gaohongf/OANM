import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Alert, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Switch, Table, Tag } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'

import { request } from '../../api/http'
import { Auth } from '../../permission/Auth'

interface ApiRoute {
  id: number
  name: string
  pathPattern: string
  uri: string
  routeOrder: number
  enabled: boolean
  locked: boolean
  source: string
}

interface ApiRouteForm {
  name: string
  pathPattern: string
  uri: string
  routeOrder: number
  enabled: boolean
}

const PERM_CREATE = 'POST:/api/auth/api-routes'
const PERM_UPDATE = 'PUT:/api/auth/api-routes/{id}'
const PERM_DELETE = 'DELETE:/api/auth/api-routes/{id}'

/**
 * 网关路由管理。
 *
 * ## 粒度是"服务前缀"，不是单个接口
 * 一行 = 一个前缀（如 `/api/ops/**`）→ 一个服务。端点级的权限不在这里，在权限管理里
 * （键形如 `GET:/api/ops/work_order/{id}`）。
 *
 * 这个划分是刻意的：路由少一行、或某行被停用，表现是**网关 404**；少一条权限数据表现是
 * **服务 403**。分开之后路由永远不会因为权限数据缺失而中断，故障现象也指向正确的方向。
 */
export default function ApiRouteList() {
  const { message } = AntdApp.useApp()
  const [routes, setRoutes] = useState<ApiRoute[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<ApiRoute | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<ApiRouteForm>()

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        setRoutes(await request<ApiRoute[]>('/api/auth/api-routes'))
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect -- 同其他列表页：silent 分支无同步 setState
    void load({ silent: true })
  }, [load])

  const openCreate = () => {
    setEditing(null)
    setModalOpen(true)
    form.setFieldsValue({
      name: '',
      pathPattern: '/api/',
      uri: 'lb://',
      routeOrder: 0,
      enabled: true,
    })
  }

  const openEdit = (record: ApiRoute) => {
    setEditing(record)
    setModalOpen(true)
    form.setFieldsValue({
      name: record.name,
      pathPattern: record.pathPattern,
      uri: record.uri,
      routeOrder: record.routeOrder,
      enabled: record.enabled,
    })
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      if (editing) {
        await request<void>(`/api/auth/api-routes/${editing.id}`, { method: 'PUT', body: values })
        message.success('修改成功，网关已热更新，无需重启')
      } else {
        await request<void>('/api/auth/api-routes', { method: 'POST', body: values })
        message.success('创建成功，网关已热更新，无需重启')
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
      await request<void>(`/api/auth/api-routes/${id}`, { method: 'DELETE' })
      message.success('删除成功')
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  return (
    <Card
      title="路由管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Auth code={PERM_CREATE}>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建路由
            </Button>
          </Auth>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="改动立刻生效"
        description="增删改都会被广播给网关并热更新，不需要重启网关。停用某条路由会让该前缀下的请求返回 404 —— 它只影响转发，不影响服务本身的鉴权。"
      />

      <Table<ApiRoute>
        rowKey="id"
        loading={loading}
        dataSource={routes}
        pagination={false}
        scroll={{ x: 'max-content' }}
        columns={[
          { title: '备注名', dataIndex: 'name', width: 180 },
          {
            title: '路径前缀',
            dataIndex: 'pathPattern',
            render: (value: string) => <Tag style={{ fontFamily: 'monospace' }}>{value}</Tag>,
          },
          {
            title: '目标服务',
            dataIndex: 'uri',
            render: (value: string) => <span style={{ fontFamily: 'monospace' }}>{value}</span>,
          },
          { title: '优先级', dataIndex: 'routeOrder', width: 90 },
          {
            title: '状态',
            width: 100,
            render: (_, record) =>
              record.enabled ? <Tag color="green">启用</Tag> : <Tag>已停用</Tag>,
          },
          {
            title: '操作',
            width: 150,
            render: (_, record) => (
              <Space>
                <Auth code={PERM_UPDATE}>
                  <Button type="link" size="small" onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={PERM_DELETE}>
                  <Popconfirm
                    title={`确定删除路由 ${record.pathPattern}？`}
                    description="删除后该前缀的请求会返回 404。"
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
        title={editing ? `编辑路由 ${editing.pathPattern}` : '新建路由'}
        open={modalOpen}
        onOk={onSubmit}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="备注名"
            rules={[
              { required: true, message: '请输入备注名' },
              { max: 64, message: '最长 64 个字符' },
            ]}
          >
            <Input placeholder="如 工单服务" />
          </Form.Item>

          <Form.Item
            name="pathPattern"
            label="路径前缀"
            extra="必须以 / 开头。粒度是服务前缀（如 /api/ops/**），不是单个接口 —— 端点级的控制用权限。"
            rules={[
              { required: true, message: '请输入路径前缀' },
              {
                pattern: /^\//,
                message: '必须以 / 开头，否则网关解析不了这条路由，而且不会报错（表现为该路由静默不生效）',
              },
            ]}
          >
            <Input placeholder="/api/ops/**" />
          </Form.Item>

          <Form.Item
            name="uri"
            label="目标服务"
            extra="服务名要写成 lb://服务名，由 Nacos 解析成真实实例。"
            rules={[{ required: true, message: '请输入目标服务' }]}
          >
            <Input placeholder="lb://service-work-order" />
          </Form.Item>

          <Space size={24}>
            <Form.Item name="routeOrder" label="优先级" extra="越小越先匹配">
              <InputNumber min={0} max={9999} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
