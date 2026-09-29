import { useCallback, useEffect, useMemo, useState } from 'react'
import { App as AntdApp, Alert, Drawer, Input, Space, Spin, Transfer } from 'antd'

import { request } from '../../../api/http'

interface Permission {
  id: number
  permissionKey: string
  label: string
  builtIn: boolean
}

interface PermissionPage {
  total: number
  records: Permission[]
}

interface TransferItem {
  key: string
  title: string
  description: string
}

interface RolePermissionDrawerProps {
  roleName: string
  onClose: () => void
  onSaved: () => void
}

/**
 * 给角色配置权限。
 *
 * ## 这里最容易出的错：只提交看得见的那部分
 * 后端的授权接口是**整体替换**语义（提交的是"最终应该拥有的全集"），而权限表可能有几百上千行、
 * 必须分页。如果界面把"当前列表里勾中的项"当成全集提交，**没显示出来的那些权限会被静默删掉** ——
 * 一次误操作就能把角色的授权清掉大半，而且界面上看不出任何异常。
 *
 * 所以这里的做法是：
 * 1. 打开时先拉<b>完整的已授权集合</b>（`GET /roles/{role}/permissions`，不分页），
 *    用它初始化 `targetKeys`
 * 2. 搜索只是把候选权限<b>并进</b> dataSource，从不动 targetKeys
 * 3. 提交时用 `targetKeys`（它始终是完整集合）
 *
 * ## 超管权限
 * 键为 `*` 的那一项只有已持有 `*` 的人才能授出去，后端会拒绝。这里把它如实列出来 ——
 * 藏起来反而会让人以为是系统漏了。
 */
export function RolePermissionDrawer({ roleName, onClose, onSaved }: RolePermissionDrawerProps) {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [keyword, setKeyword] = useState('')

  /** 已授权集合（完整，不受搜索影响） */
  const [granted, setGranted] = useState<Permission[]>([])
  /**
   * 默认候选池：权限列表的第一页。
   * <p>
   * 必须有它：左侧显示的是"候选池里不在 targetKeys 中的项"，而只放已授权项的话，
   * 它们全都在右侧 —— 左侧会是空的，看起来像"没有权限可选"。
   */
  const [defaultPool, setDefaultPool] = useState<Permission[]>([])
  /** 搜索结果；null 表示当前没有搜索，用默认候选池 */
  const [searchResult, setSearchResult] = useState<Permission[] | null>(null)
  const [targetKeys, setTargetKeys] = useState<string[]>([])

  /**
   * 候选池 = 已授权 ∪ （搜索结果 或 默认池）。
   * <p>
   * 并上已授权项是必需的：Transfer 靠 dataSource 渲染右侧列表，已授权项不在里面的话
   * 右侧会显示成 id 而不是名称。
   */
  const candidates = useMemo(
    () => mergeById(granted, searchResult ?? defaultPool),
    [granted, searchResult, defaultPool],
  )

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      try {
        const [rolePermissions, firstPage] = await Promise.all([
          request<Permission[]>('/api/auth/roles/' + encodeURIComponent(roleName) + '/permissions'),
          // 拉第一页作为默认候选。用 50 而不是 10，让左侧一屏能看到足够多的可选项。
          request<PermissionPage>('/api/auth/permissions', { query: { current: 1, size: 50 } }),
        ])
        if (cancelled) {
          return
        }
        setGranted(rolePermissions)
        setDefaultPool(firstPage.records)
        setTargetKeys(rolePermissions.map((item) => String(item.id)))
      } catch (error) {
        message.error(error instanceof Error ? error.message : '加载权限失败')
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [roleName, message])

  /**
   * 搜索：把匹配到的权限作为候选池，绝不改动 targetKeys。
   * <p>
   * 「不改动 targetKeys」是这一页最要紧的事：授权是整体替换语义，一旦搜索影响了选中集，
   * 保存时就会把没搜到的权限删掉。
   */
  const onSearch = useCallback(
    async (value: string) => {
      setKeyword(value)
      if (!value.trim()) {
        // 清空搜索回到默认候选池（而不是回到"只有已授权项"，那会让左侧又变空）
        setSearchResult(null)
        return
      }
      try {
        const page = await request<PermissionPage>('/api/auth/permissions', {
          query: { current: 1, size: 50, keyword: value.trim() },
        })
        setSearchResult(page.records)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '搜索权限失败')
      }
    },
    [message],
  )

  const onSave = async () => {
    setSaving(true)
    try {
      // targetKeys 是**完整**的已授权集合（不是增量、也不是当前可见的那部分）
      const permissionIds = targetKeys.map((key) => Number(key))
      await request<void>(`/api/auth/roles/${encodeURIComponent(roleName)}/permissions`, {
        method: 'PUT',
        body: { permissionIds },
      })
      message.success('保存成功')
      onSaved()
      onClose()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer
      title={`配置「${roleName}」的权限`}
      width={860}
      open
      onClose={onClose}
      destroyOnHidden
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索权限（按标识或标签）"
            onSearch={onSearch}
            style={{ width: 280 }}
          />
          <a
            onClick={() => {
              if (saving) {
                return
              }
              void onSave()
            }}
          >
            {saving ? '保存中...' : '保存'}
          </a>
        </Space>
      }
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="保存是整体替换，不是增量"
        description="右边是保存后该角色拥有的全部权限。左侧默认列出权限表的前 50 条，用上面的搜索框可以找其他的。搜索只影响左侧能选什么，不会动右边已授权的项 —— 所以搜不到某条权限并不会把它移除。"
      />

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <Transfer
          // 目标集合是完整的已授权集合，因此 dataSource 必须包含它们，
          // 否则右侧列表会因为找不到数据项而显示成 key 而不是名称
          dataSource={toTransferItems(candidates)}
          targetKeys={targetKeys}
          onChange={(nextKeys) => setTargetKeys(nextKeys as string[])}
          titles={['候选权限', `已授权（${targetKeys.length}）`]}
          showSearch={false}
          listStyle={{ width: 380, height: 460 }}
          locale={{
            itemUnit: '条',
            itemsUnit: '条',
            // 左侧为空时的提示。最常见的两种情况：搜索没匹配到，或者候选池里的项
            // 已经全被移到右侧了 —— 后者会让人以为"没有权限可选"，所以要指出来。
            notFoundContent: keyword ? '没有匹配的权限' : '这些权限都已经授权了，用搜索找其他的',
          }}
          render={(item) => (
            <span title={item.description}>
              {item.title}
              <span style={{ color: 'rgba(0,0,0,0.45)', marginInlineStart: 8 }}>{item.description}</span>
            </span>
          )}
        />
      )}

      {keyword && (
        <div style={{ marginTop: 12, color: 'rgba(0,0,0,0.45)' }}>
          左侧显示的是「{keyword}」的搜索结果（共 {searchResult?.length ?? 0} 条）。清空搜索框会回到默认列表。
        </div>
      )}
    </Drawer>
  )
}

/** 按 id 合并两份权限列表，保持已有顺序 */
function mergeById(current: Permission[], incoming: Permission[]): Permission[] {
  const seen = new Set(current.map((item) => item.id))
  const merged = [...current]
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      merged.push(item)
      seen.add(item.id)
    }
  }
  return merged
}

function toTransferItems(permissions: Permission[]): TransferItem[] {
  return permissions.map((item) => ({
    key: String(item.id),
    title: item.label,
    description: item.permissionKey,
  }))
}
