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
import { usePermission } from '../../permission/usePermission'
import { availableComponents } from '../../router/registry'
import type { MenuNode } from '../../types/api'
import {
  collectMenuKeys,
  containsMenuNode,
  findMenuNode,
  planMenuMove,
} from './components/menuDrag'
import type { MenuDropPosition, MenuMovePlan } from './components/menuDrag'
import './components/menuDrag.css'

const { Paragraph, Text } = Typography

interface MenuForm {
  parentId: string | null
  name: string
  path?: string
  component?: string
  icon?: string
  sort: number
  hidden: boolean
  keepAlive: boolean
  /** 门禁权限 id 集合；[] = 登录即可见。整体替换语义 */
  permissionIds: string[]
  enabled: boolean
}

interface PermissionOption {
  /** 字符串而不是数字：雪花 id 超过 2^53 在 JS 里会被取整 */
  id: string
  permissionKey: string
  label: string
}

const PERM_CREATE = 'POST:/api/auth/menus'
const PERM_UPDATE = 'PUT:/api/auth/menus/{id}'
const PERM_DELETE = 'DELETE:/api/auth/menus/{id}'

const PERMISSION_PAGE_SIZE = 200
const MAX_PERMISSION_PAGES = 20

/**
 * 权限下拉的全部选项。
 *
 * 接口按 `permission_key` 升序分页，`POST:` / `PUT:` 排在 `GET:` 后面 ——
 * 只取第一页时，条数一多，要给菜单配的写权限可能根本不在选项里。
 * 所以先取第一页拿 `total`，再并行取剩下的页。
 *
 * 上限 20 页（4000 条），超过时接受"只拿到前 4000 条"的限制；
 * 真要更多，应该换服务端搜索，而不是在这里继续加页数。
 */
async function loadPermissionOptions(): Promise<PermissionOption[]> {
  const first = await request<{ records: PermissionOption[]; total: number }>(
    '/api/auth/permissions',
    { query: { current: 1, size: PERMISSION_PAGE_SIZE } },
  )
  const pageCount = Math.min(
    Math.ceil(first.total / PERMISSION_PAGE_SIZE),
    MAX_PERMISSION_PAGES,
  )
  if (pageCount <= 1) {
    return first.records
  }
  const rest = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) =>
      request<{ records: PermissionOption[] }>('/api/auth/permissions', {
        query: { current: index + 2, size: PERMISSION_PAGE_SIZE },
      }),
    ),
  )
  return [first.records, ...rest.map((page) => page.records)].flat()
}

/**
 * 菜单管理。
 *
 * 这一页是"菜单从后端来"这件事的闭环：菜单在这里配置，前端登录后拉的菜单树就是它的结果，
 * 路由与侧边栏都从那份数据生成。
 *
 * `component` 用下拉而不是自由输入：可选项来自前端**白名单**（`availableComponents`），
 * 所以不可能配出一个打不开的页面。后端存的是相对 `src/pages/` 的文件路径，
 * 与这里的候选项逐字一致（大小写敏感）。
 *
 * ## 拖动调整结构
 * 行中部 = 挂为目标节点的子项，行的上/下边缘 = 与目标同级换序，顶部虚线区 = 移到顶级。
 * 移动复用现有的 `PUT /api/auth/menus/{id}`，不额外加接口：后端本来就会校验父子关系、
 * 拒绝把节点挂到自己的子孙下面。
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

  const canUpdate = usePermission(PERM_UPDATE)
  const [expandedKeys, setExpandedKeys] = useState<string[]>([])
  const [dragging, setDragging] = useState<MenuNode | null>(null)
  const [dropTarget, setDropTarget] = useState<{
    id: string
    position: MenuDropPosition
  } | null>(null)
  const [rootDropActive, setRootDropActive] = useState(false)
  const [moving, setMoving] = useState(false)

  /** @param silent 静默刷新（挂载时用）：不切换 loading，理由同 RoleList */
  const load = useCallback(
    async ({ silent = false }: { silent?: boolean } = {}) => {
      if (!silent) {
        setLoading(true)
      }
      try {
        // 权限列表用于给菜单绑定门禁。它可能上千行（自注册是"一个端点一条"），
        // 所以按 total 取完所有页，见 loadPermissionOptions。
        const [tree, loadedPermissions] = await Promise.all([
          request<MenuNode[]>('/api/auth/menus'),
          loadPermissionOptions(),
        ])
        setMenus(tree)
        // 展开状态自己管：拖动后整棵树会重新拉取，defaultExpandAllRows 只在首次生效
        setExpandedKeys(collectMenuKeys(tree))
        setPermissions(loadedPermissions)
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

  const openCreate = (parentId: string | null) => {
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
      permissionIds: [],
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
      permissionIds: record.permissionIds ?? [],
      enabled: record.enabled,
    })
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    // 多选清空后 antd 给的是 undefined。必须显式转成 []：后端把 null 当作"没传这个字段"
    // （用于兼容旧前端的单个 permissionId），空数组才是"整体替换为空"。
    const body = { ...values, permissionIds: values.permissionIds ?? [] }
    try {
      if (editing) {
        await request<void>(`/api/auth/menus/${editing.id}`, { method: 'PUT', body })
        message.success('修改成功')
      } else {
        await request<void>('/api/auth/menus', { method: 'POST', body })
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

  const onDelete = async (id: string) => {
    try {
      await request<void>(`/api/auth/menus/${id}`, { method: 'DELETE' })
      message.success('删除成功')
      await load()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败')
    }
  }

  const clearDrag = useCallback(() => {
    setDragging(null)
    setDropTarget(null)
    setRootDropActive(false)
  }, [])

  /**
   * 逐条写回移动计划。
   *
   * 同级换序可能要改多行（整组兄弟重新编号），做不到一条 SQL 的原子性；
   * 任何一条失败都重新拉一次，以服务端的真实状态为准，而不是让界面停在半成品上。
   */
  const applyMove = async (plan: MenuMovePlan[]) => {
    if (plan.length === 0) {
      clearDrag()
      return
    }
    setMoving(true)
    try {
      for (const update of plan) {
        const node = findMenuNode(menus, update.id)
        if (!node) {
          continue
        }
        await request<void>(`/api/auth/menus/${update.id}`, {
          method: 'PUT',
          body: {
            parentId: update.parentId,
            name: node.name,
            path: node.path ?? undefined,
            component: node.component ?? undefined,
            icon: node.icon ?? undefined,
            sort: update.sort,
            hidden: node.hidden,
            keepAlive: node.keepAlive,
            // 拖动会回写整行：漏掉这个字段就等于静默清空门禁
            permissionIds: node.permissionIds ?? [],
            enabled: node.enabled,
          },
        })
      }
      message.success('菜单已移动')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '移动失败')
    } finally {
      setMoving(false)
      clearDrag()
      await load({ silent: true })
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
      {canUpdate ? (
        <>
          <Paragraph type="secondary" style={{ marginBottom: 12 }}>
            拖动行可调整菜单结构：放在行中部 = 挂为子项，放在行的上/下边缘 = 同级排序。
          </Paragraph>
          <div
            onDragOver={(event) => {
              if (!dragging || moving) {
                return
              }
              event.preventDefault()
              event.dataTransfer.dropEffect = 'move'
              setRootDropActive(true)
              setDropTarget(null)
            }}
            onDragLeave={() => setRootDropActive(false)}
            onDrop={(event) => {
              event.preventDefault()
              if (!dragging) {
                return
              }
              const others = menus.filter((node) => node.id !== dragging.id)
              const maxSort = others.reduce((max, node) => Math.max(max, node.sort), 0)
              // 已经是根节点、且本来就排在最后：不用写库
              const plan: MenuMovePlan[] =
                dragging.parentId === null && dragging.sort > maxSort
                  ? []
                  : [{ id: dragging.id, parentId: null, sort: maxSort + 1 }]
              void applyMove(plan)
            }}
            style={{
              marginBottom: 12,
              padding: '8px 12px',
              border: `1px dashed ${
                rootDropActive
                  ? 'var(--ant-color-primary, #1677ff)'
                  : 'var(--ant-color-border, #d9d9d9)'
              }`,
              borderRadius: 8,
              textAlign: 'center',
              color: rootDropActive
                ? 'var(--ant-color-primary, #1677ff)'
                : 'var(--ant-color-text-tertiary, #8c8c8c)',
              background: rootDropActive
                ? 'var(--ant-color-primary-bg, #e6f4ff)'
                : 'transparent',
              transition: 'all 0.2s',
            }}
          >
            拖到这里移到顶级菜单
          </div>
        </>
      ) : null}

      <Table<MenuNode>
        rowKey="id"
        loading={loading || moving}
        dataSource={menus}
        pagination={false}
        scroll={{ x: 'max-content' }}
        // 展开状态自己管：拖动后整棵树会重新拉取
        expandable={{
          expandedRowKeys: expandedKeys,
          onExpandedRowsChange: (keys) => setExpandedKeys(keys.map(String)),
        }}
        rowClassName={(record) => {
          const classes: string[] = []
          if (canUpdate) {
            classes.push('menu-drag-row')
          }
          if (dragging?.id === record.id) {
            classes.push('menu-drag-ghost')
          }
          if (dropTarget?.id === record.id) {
            classes.push(`menu-drag-${dropTarget.position}`)
          }
          return classes.join(' ')
        }}
        onRow={(record) => ({
          draggable: canUpdate && !moving,
          onDragStart: (event) => {
            if (!canUpdate || moving) {
              event.preventDefault()
              return
            }
            event.dataTransfer.effectAllowed = 'move'
            // Firefox 必须 setData 才会真正开始拖动
            event.dataTransfer.setData('text/plain', record.id)
            setDragging(record)
          },
          onDragOver: (event) => {
            if (!dragging || moving) {
              return
            }
            // 拖到自己或自己的子孙上会成环：不接收，也不显示落点
            if (record.id === dragging.id || containsMenuNode(dragging, record.id)) {
              setDropTarget(null)
              return
            }
            event.preventDefault()
            event.dataTransfer.dropEffect = 'move'
            const rect = event.currentTarget.getBoundingClientRect()
            const ratio = (event.clientY - rect.top) / rect.height
            const position: MenuDropPosition =
              ratio < 0.3 ? 'before' : ratio > 0.7 ? 'after' : 'inside'
            setDropTarget((prev) =>
              prev && prev.id === record.id && prev.position === position
                ? prev
                : { id: record.id, position },
            )
            setRootDropActive(false)
          },
          onDragLeave: (event) => {
            // 在行内单元格之间移动也会触发 dragleave，relatedTarget 还在行里就不清
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
              setDropTarget((prev) => (prev?.id === record.id ? null : prev))
            }
          },
          onDrop: (event) => {
            event.preventDefault()
            if (!dragging || !dropTarget || dropTarget.id !== record.id) {
              return
            }
            void applyMove(planMenuMove(menus, dragging.id, record.id, dropTarget.position))
          },
          onDragEnd: clearDrag,
        })}
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
            title: '所需权限（任一）',
            width: 260,
            render: (_, r) => {
              // 缺字段兜底：发布窗口里旧后端可能还没有这两个数组，不能让它把整页渲染崩掉
              const ids = r.permissionIds ?? []
              const keys = r.permissionKeys ?? []
              return ids.length > 0 ? (
                <Space size={[4, 4]} wrap>
                  {ids.map((id, index) => {
                    // 按位置取键：两个数组后端保证同长同序，不能过滤 null（会错位）
                    const key = keys[index]
                    return key ? (
                      <Text code key={id}>
                        {key}
                      </Text>
                    ) : (
                      <Tag color="red" key={id}>
                        已失效 {id}
                      </Tag>
                    )
                  })}
                </Space>
              ) : (
                <Text type="secondary">登录即可见</Text>
              )
            },
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

          <Form.Item
            name="permissionIds"
            label="所需权限"
            extra="可多选：持有任意一个即可见；留空表示登录即可见。"
          >
            <Select
              mode="multiple"
              allowClear
              showSearch
              optionFilterProp="label"
              maxTagCount="responsive"
              placeholder="留空表示登录即可见"
              options={permissionOptions}
            />
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
