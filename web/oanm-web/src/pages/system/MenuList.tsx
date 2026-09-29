import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { TreeSelectProps } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'

import { request } from '../../api/http'
import { Auth } from '../../permission/Auth'
import { availableComponents } from '../../router/registry'
import type { MenuNode } from '../../types/api'

const { Text } = Typography

interface MenuForm {
  parentId: number | null
  name: string
  path?: string
  component?: string
  icon?: string
  sort: number
  hidden: boolean
  keepAlive: boolean
  permissionId: number | null
  enabled: boolean
}

interface PermissionOption {
  id: number
  permissionKey: string
  label: string
}

const PERM_CREATE = 'POST:/api/auth/menus'
const PERM_UPDATE = 'PUT:/api/auth/menus/{id}'
const PERM_DELETE = 'DELETE:/api/auth/menus/{id}'

/**
 * 菜单管理。
 *
 * 这一页是"菜单从后端来"这件事的闭环：菜单在这里配置，前端登录后拉的菜单树就是它的结果，
 * 路由与侧边栏都从那份数据生成。
 *
 * `component` 用下拉而不是自由输入：可选项来自前端**白名单**（`availableComponents`），
 * 所以不可能配出一个打不开的页面。后端存的是相对 `src/pages/` 的文件路径，
 * 与这里的候选项逐字一致（大小写敏感）。
 */
export default function MenuList() {
  const { message } = AntdApp.useApp()
  const [menus, setMenus] = useState<MenuNode[]>([])
  const [permissions, setPermissions] = useState<PermissionOption[]>([])
  // 初始就是 true: 挂载后会立刻拉一次，见 RoleList 里的同款说明
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<MenuNode | null>(null)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm<MenuForm>()

  /** @param silent 静默刷新（挂载时用）：不切换 loading，理由同 RoleList */
  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        // 权限列表用于给菜单绑定门禁。它可能上千行（自注册是"一个端点一条"），
        // 所以拉大一点但仍走分页接口，而不是要求一个"全量"端点。
        const [tree, page] = await Promise.all([
          request<MenuNode[]>('/api/auth/menus'),
          request<{ records: PermissionOption[] }>('/api/auth/permissions', {
            query: { current: 1, size: 200 },
          }),
        ])
        setMenus(tree)
        setPermissions(page.records)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载失败')
      } finally {
        setLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect -- 理由同 RoleList：silent 分支不含同步 setState
    void load({ silent: true })
  }, [load])

  /** 供"上级菜单"选择用：目录本身不能当页面，但可以当父节点 */
  const parentOptions = useMemo(() => toTreeSelectData(menus), [menus])

  const permissionOptions = useMemo(
    () => permissions.map((p) => ({ value: p.id, label: `${p.label}（${p.permissionKey}）` })),
    [permissions],
  )

  const openCreate = (parentId: number | null) => {
    setEditing(null)
    setCreating(true)
    form.setFieldsValue({
      parentId,
      name: '',
      path: undefined,
      component: undefined,
      icon: undefined,
      sort: 0,
      hidden: false,
      keepAlive: false,
      permissionId: null,
      enabled: true,
    })
  }

  const openEdit = (record: MenuNode) => {
    setEditing(record)
    setCreating(true)
    form.setFieldsValue({
      parentId: record.parentId,
      name: record.name,
      path: record.path ?? undefined,
      component: record.component ?? undefined,
      icon: record.icon ?? undefined,
      sort: record.sort,
      hidden: record.hidden,
      keepAlive: record.keepAlive,
      permissionId: record.permissionId,
      enabled: record.enabled,
    })
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await request<void>(`/api/auth/menus/${editing.id}`, { method: 'PUT', body: values })
        message.success('修改成功')
      } else {
        await request<void>('/api/auth/menus', { method: 'POST', body: values })
        message.success('创建成功')
      }
      setCreating(false)
      await load()
    } catch (error) {
      // 后端会拒绝"挂到自己的子节点下面"这类会成环的移动，以及删除有子节点的菜单。
      // 如实展示它的提示 —— 这些是真实约束，不该被前端包装成模糊的"操作失败"。
      message.error(error instanceof Error ? error.message : '保存失败')
    }
  }

  const onDelete = async (id: number) => {
    try {
      await request<void>(`/api/auth/menus/${id}`, { method: 'DELETE' })
      message.success('删除成功')
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  return (
    <Card
      title="菜单管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Auth code={PERM_CREATE}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(null)}>
              新建根菜单
            </Button>
          </Auth>
        </Space>
      }
    >
      <Table<MenuNode>
        rowKey="id"
        loading={loading}
        dataSource={menus}
        pagination={false}
        scroll={{ x: 'max-content' }}
        // 菜单本身就是树，直接把 children 交给表格渲染成可展开的层级
        expandable={{ defaultExpandAllRows: true }}
        columns={[
          { title: '名称', dataIndex: 'name', width: 200 },
          {
            title: '类型',
            width: 80,
            render: (_, r) => (r.component ? <Tag color="blue">页面</Tag> : <Tag>目录</Tag>),
          },
          { title: '路由', dataIndex: 'path', width: 160, render: (v: string | null) => v ?? '-' },
          {
            title: '组件',
            dataIndex: 'component',
            width: 200,
            render: (v: string | null) => (v ? <Text code>{v}</Text> : '-'),
          },
          {
            title: '所需权限',
            width: 220,
            render: (_, r) =>
              r.permissionKey ? (
                <Text code>{r.permissionKey}</Text>
              ) : (
                <Text type="secondary">登录即可见</Text>
              ),
          },
          { title: '排序', dataIndex: 'sort', width: 70 },
          {
            title: '状态',
            width: 100,
            render: (_, r) => (
              <Space size={4}>
                {!r.enabled && <Tag>已停用</Tag>}
                {r.hidden && <Tag>隐藏</Tag>}
                {r.enabled && !r.hidden && <Tag color="green">显示</Tag>}
              </Space>
            ),
          },
          {
            title: '操作',
            width: 200,
            render: (_, r) => (
              <Space>
                <Auth code={PERM_CREATE}>
                  <Button type="link" size="small" onClick={() => openCreate(r.id)}>
                    添加子项
                  </Button>
                </Auth>
                <Auth code={PERM_UPDATE}>
                  <Button type="link" size="small" onClick={() => openEdit(r)}>
                    编辑
                  </Button>
                </Auth>
                <Auth code={PERM_DELETE}>
                  <Popconfirm
                    title={`确定删除「${r.name}」？`}
                    description="有子菜单时后端会拒绝删除。"
                    onConfirm={() => onDelete(r.id)}
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
        title={editing ? `编辑「${editing.name}」` : '新建菜单'}
        open={creating}
        onOk={onSubmit}
        onCancel={() => setCreating(false)}
        width={620}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="parentId" label="上级菜单">
            <TreeSelect
              allowClear
              placeholder="留空表示顶级菜单"
              treeData={parentOptions}
              treeDefaultExpandAll
              // 叶子节点也允许被选为父节点（目录下面再挂目录是合法的）
              treeNodeFilterProp="title"
            />
          </Form.Item>

          <Form.Item
            name="name"
            label="名称"
            rules={[
              { required: true, message: '请输入名称' },
              { max: 64, message: '最长 64 个字符' },
            ]}
          >
            <Input placeholder="如 工单管理" />
          </Form.Item>

          <Form.Item
            name="component"
            label="页面组件"
            extra="留空即「目录」，只用于分组、不产生路由。选项来自前端白名单，所以配不出打不开的页面。"
          >
            <Select allowClear showSearch placeholder="选择一个页面" options={toComponentOptions()} />
          </Form.Item>

          <Form.Item
            name="path"
            label="前端路由"
            dependencies={['component']}
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value: string | undefined) {
                  // 与后端的交叉校验一致：配了组件就必须有路由，否则前端注册不出路由、点开是白屏。
                  // 两边都校验一次是刻意的 —— 前端这份只是即时反馈。
                  if (getFieldValue('component') && !value) {
                    return Promise.reject(new Error('配置了页面组件就必须填写前端路由'))
                  }
                  return Promise.resolve()
                },
              }),
            ]}
          >
            <Input placeholder="如 /ops/work-order" />
          </Form.Item>

          <Form.Item name="icon" label="图标" extra="取自前端白名单；填了不存在的名字会退化成默认图标。">
            <Input placeholder="dashboard / setting / file / team / user / api" />
          </Form.Item>

          <Form.Item name="permissionId" label="所需权限" extra="留空表示登录即可见。">
            <Select allowClear showSearch optionFilterProp="label" options={permissionOptions} />
          </Form.Item>

          <Space size={24}>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} max={9999} />
            </Form.Item>
            <Form.Item name="hidden" label="侧边栏隐藏" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="keepAlive" label="缓存页面" valuePropName="checked">
              <Switch />
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

/**
 * 菜单树 → TreeSelect 数据。
 *
 * 返回类型直接取自 antd 的 props 类型（`NonNullable<...>` 去掉 undefined），
 * 而不是自己写一个结构 —— 自己写的结构一旦和 antd 的 `DataNode` 有细微出入，
 * 就会在调用处报一个看不懂的赋值错误。
 *
 * 没有子节点时不下发 `children` 字段（而不是给个 undefined）：antd 会用
 * "有没有 children"来判断是否显示展开箭头，给了空数组会渲染出一个空的展开图标。
 */
function toTreeSelectData(nodes: MenuNode[]): NonNullable<TreeSelectProps['treeData']> {
  return nodes.map((node) => {
    const children = toTreeSelectData(node.children)
    return children.length > 0
      ? { value: node.id, title: node.name, children }
      : { value: node.id, title: node.name }
  })
}

/** 白名单里的组件名 → Select 选项 */
function toComponentOptions() {
  return availableComponents.map((value) => ({ value, label: value }))
}
