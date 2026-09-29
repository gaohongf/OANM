import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Input, Modal, Select, Space, Table, Tag } from 'antd'
import { ReloadOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons'

import { request } from '../../api/http'
import { Auth } from '../../permission/Auth'

interface UserRow {
  id: number
  username: string
  nickname: string
  locked: boolean
  /** 后端一次带出来的角色，分配弹窗直接用它做初始值，不用再查一次 */
  roles: string[]
}

interface UserPage {
  total: number
  current: number
  size: number
  records: UserRow[]
}

interface Role {
  roleName: string
  label: string
}

const PERM_GRANT = 'PUT:/api/auth/users/{id}/roles'

/**
 * 用户管理（目前只有"分配角色"这一项写操作）。
 *
 * ## 为什么角色列表和用户列表一起加载
 * 分配角色的弹窗需要全量角色。角色量级很小（个位数到几十），一次拉完比"打开弹窗再拉"
 * 少一次往返，也避免了弹窗里的选项列表在不同用户之间闪一下。
 */
export default function UserList() {
  const { message } = AntdApp.useApp()
  const [records, setRecords] = useState<UserRow[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [assigning, setAssigning] = useState<UserRow | null>(null)
  const [selectedRoles, setSelectedRoles] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        const [page, roleList] = await Promise.all([
          request<UserPage>('/api/auth/users', {
            query: { current, size, keyword: keyword.trim() || undefined },
          }),
          request<Role[]>('/api/auth/roles'),
        ])
        setRecords(page.records)
        setTotal(page.total)
        setRoles(roleList)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [current, size, keyword, message],
  )

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect -- 同其他列表页：silent 分支无同步 setState
    void load({ silent: true })
  }, [load])

  const openAssign = (user: UserRow) => {
    setAssigning(user)
    setSelectedRoles(user.roles)
  }

  const onSubmitRoles = async () => {
    if (!assigning) {
      return
    }
    setSubmitting(true)
    try {
      // 整体替换语义：提交的是"这个人最终应该拥有的角色全集"。
      // 所以初始值必须来自后端返回的完整角色列表（上面 openAssign 就是干这个的），
      // 不能提交一个只包含本次勾选增量的集合 —— 那会把没勾到的角色全删掉。
      await request<void>(`/api/auth/users/${assigning.id}/roles`, {
        method: 'PUT',
        body: { roleNames: selectedRoles },
      })
      message.success('分配成功')
      setAssigning(null)
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '分配失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card
      title="用户管理"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="按用户名或昵称搜索"
            enterButton={<SearchOutlined />}
            onSearch={(value) => {
              setKeyword(value)
              // 换搜索词回第 1 页，否则可能停在一个空页上
              setCurrent(1)
            }}
            style={{ width: 260 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>
      }
    >
      <Table<UserRow>
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
          { title: '用户名', dataIndex: 'username', width: 160 },
          { title: '昵称', dataIndex: 'nickname', width: 160 },
          {
            title: '状态',
            width: 90,
            render: (_, record) => (record.locked ? <Tag color="red">已封号</Tag> : <Tag color="green">正常</Tag>),
          },
          {
            title: '角色',
            render: (_, record) =>
              record.roles.length > 0 ? (
                record.roles.map((role) => <Tag key={role}>{role}</Tag>)
              ) : (
                <span style={{ color: 'rgba(0,0,0,0.45)' }}>无角色（登录后看不到任何菜单）</span>
              ),
          },
          {
            title: '操作',
            width: 120,
            render: (_, record) => (
              <Auth code={PERM_GRANT}>
                <Button type="link" size="small" icon={<TeamOutlined />} onClick={() => openAssign(record)}>
                  分配角色
                </Button>
              </Auth>
            ),
          },
        ]}
      />

      <Modal
        title={assigning ? `给「${assigning.nickname || assigning.username}」分配角色` : '分配角色'}
        open={assigning !== null}
        onOk={onSubmitRoles}
        confirmLoading={submitting}
        onCancel={() => setAssigning(null)}
        destroyOnHidden
      >
        <Select
          mode="multiple"
          allowClear
          style={{ width: '100%' }}
          placeholder="选择这个用户最终应该拥有的角色"
          value={selectedRoles}
          onChange={setSelectedRoles}
          options={roles.map((role) => ({ value: role.roleName, label: `${role.label}（${role.roleName}）` }))}
        />
        <div style={{ marginTop: 12, color: 'rgba(0,0,0,0.45)' }}>
          这里提交的是<b>最终结果</b>：清空选项就等于收回该用户的全部角色。
        </div>
      </Modal>
    </Card>
  )
}
