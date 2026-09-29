import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Form, Input, Modal, Popconfirm, Space, Table } from 'antd'
import { PlusOutlined } from '@ant-design/icons'

import { request } from '../../api/http'
import { Auth } from '../../permission/Auth'
import { usePermission } from '../../permission/usePermission'
import { RolePermissionDrawer } from './components/RolePermissionDrawer'

interface Role {
  roleName: string
  label: string
}

interface RoleForm {
  roleName: string
  label: string
}

/** 这些权限键要与后端接口的 `METHOD:路径模式` 逐字一致 */
const PERM_CREATE = 'POST:/api/auth/roles'
const PERM_DELETE = 'DELETE:/api/auth/roles/{roleName}'
const PERM_GRANT = 'PUT:/api/auth/roles/{roleName}/permissions'

/**
 * 角色管理。
 *
 * 这一页用来演示按钮级权限：按钮的显隐由 `<Auth>` 控制，但**后端仍然会独立校验** ——
 * 把按钮藏起来只是让界面干净，不是安全边界。
 */
export default function RoleList() {
  const { message } = AntdApp.useApp()
  const [roles, setRoles] = useState<Role[]>([])
  // 初始就是 true: 挂载后会立刻拉一次, 所以"正在加载"从一开始就是事实。
  // 初始给 false 会让表格先渲染一帧"空数据"再翻成加载中，看起来是闪了一下。
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  /** 正在配置权限的角色名；null 表示抽屉关闭 */
  const [grantingRole, setGrantingRole] = useState<string | null>(null)
  const [form] = Form.useForm<RoleForm>()

  const canCreate = usePermission(PERM_CREATE)
  const canDelete = usePermission(PERM_DELETE)
  const canGrant = usePermission(PERM_GRANT)

  /**
   * @param silent 静默刷新：不切换 loading 状态。
   *   挂载时用它 —— 首次加载的 loading 已经由初始 state 表达，在 effect 里再同步
   *   setState 一次只会多触发一轮渲染（React 的 set-state-in-effect 就是这么判的）。
   *   用户手动点刷新时则要显示 loading，所以那种调用不加这个参数。
   */
  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        setRoles(await request<Role[]>('/api/auth/roles'))
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    // 挂载时拉一次数据是这个页面的正当职责。linter 看到 load 里含 setState 就判定为
    // "在 effect 里同步 setState"，但它不跨 await 分析：挂载时传的是 silent，
    // 那条分支里没有同步 setState。改用数据请求库是更大的决定，不为了消一条警告引入。
    // 注意 disable 注释必须紧贴目标行 —— 中间夹一行普通注释就会作用到注释上，形同虚设。
    // eslint-disable-next-line react/set-state-in-effect
    void load({ silent: true })
  }, [load])

  const onSubmit = async () => {
    // 先过表单校验（standard.md：用户输入不可信，都需要校验）。
    // 后端还有一遍 @Valid + check()，两边都保留是刻意的。
    const values = await form.validateFields()
    try {
      await request<void>('/api/auth/roles', { method: 'POST', body: values })
      message.success('创建成功')
      setCreating(false)
      form.resetFields()
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败')
    }
  }

  const onDelete = async (roleName: string) => {
    try {
      await request<void>(`/api/auth/roles/${encodeURIComponent(roleName)}`, { method: 'DELETE' })
      message.success('删除成功')
      await load()
    } catch (error) {
      // 被用户持有的角色后端会拒绝（409），这里如实展示后端给的提示
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  return (
    <Card
      title="角色管理"
      extra={
        <Auth code={PERM_CREATE}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            新建角色
          </Button>
        </Auth>
      }
    >
      <Table<Role>
        rowKey="roleName"
        loading={loading}
        dataSource={roles}
        pagination={false}
        scroll={{ x: 'max-content' }}
        columns={[
          { title: '角色名', dataIndex: 'roleName' },
          { title: '标签', dataIndex: 'label' },
          {
            title: '操作',
            width: 200,
            render: (_, record) => (
              <Space>
                <Auth code={PERM_GRANT}>
                  <Button type="link" size="small" onClick={() => setGrantingRole(record.roleName)}>
                    配置权限
                  </Button>
                </Auth>
                <Auth code={PERM_DELETE}>
                  <Popconfirm title={`确定删除角色 ${record.roleName}？`} onConfirm={() => onDelete(record.roleName)}>
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
        title="新建角色"
        open={creating}
        onOk={onSubmit}
        onCancel={() => setCreating(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="roleName"
            label="角色名"
            rules={[
              { required: true, message: '请输入角色名' },
              { min: 2, max: 64, message: '长度为 2 到 64 个字符' },
            ]}
          >
            <Input placeholder="如 viewer（创建后不可修改）" />
          </Form.Item>
          <Form.Item
            name="label"
            label="标签"
            rules={[
              { required: true, message: '请输入标签' },
              { max: 64, message: '最长 64 个字符' },
            ]}
          >
            <Input placeholder="如 只读用户" />
          </Form.Item>
        </Form>
      </Modal>

      {!canCreate && !canDelete && !canGrant && (
        <div style={{ marginTop: 12, color: 'rgba(0,0,0,0.45)' }}>
          你只能查看角色，没有新建、删除或配置权限的权限。
        </div>
      )}

      {grantingRole !== null && (
        <RolePermissionDrawer
          roleName={grantingRole}
          onClose={() => setGrantingRole(null)}
          onSaved={() => void load()}
        />
      )}
    </Card>
  )
}
