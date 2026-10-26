import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Result,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Tabs,
} from 'antd'
import { EditOutlined, EyeOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router'

import {
  createKnowledgeArticle,
  getManageKnowledge,
  KNOWLEDGE_PERMISSIONS,
  listKnowledgeCategories,
  updateKnowledgeArticle,
} from '../../api/knowledge'
import type {
  ArticleStatus,
  KnowledgeCategory,
  SaveKnowledgeArticlePayload,
} from '../../api/knowledge'
import { usePermission } from '../../permission/usePermission'
import { KnowledgeMarkdown } from './components/KnowledgeMarkdown'

/** 编辑器挂在「知识管理」下，返回时统一回到列表 */
const MANAGE_PATH = '/knowledge/manage'

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
]

interface ArticleFormValues {
  title: string
  summary?: string
  content: string
  categoryId: string
  status: ArticleStatus
}

/**
 * 新建 / 编辑知识库文章。
 *
 * ## 为什么是独立页面而不是弹窗
 * 正文是整篇 Markdown，写到一半点到遮罩或按 Esc 就丢掉太容易了；独立页面也没有
 * 弹窗的宽度限制，可以左边放属性、右边放正文。
 *
 * ## 路由由菜单下发
 * 菜单项「知识库 → 新建文章」指向 `/knowledge/manage/editor`，侧栏可以直接进入；
 * 列表页的「新建 / 编辑」按钮也跳到同一个路径（编辑带 `?id=`）。路由随菜单树生成，
 * 所以权限在这里再判一次：菜单只是入口，不负责拦人。
 */
export default function KnowledgeEditor() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const editingId = searchParams.get('id')

  const canCreate = usePermission(KNOWLEDGE_PERMISSIONS.create)
  const canUpdate = usePermission(KNOWLEDGE_PERMISSIONS.update)
  const canManageDetail = usePermission(KNOWLEDGE_PERMISSIONS.manageDetail)

  const allowed = editingId ? canUpdate && canManageDetail : canCreate
  if (!allowed) {
    return (
      <Result
        status="403"
        title="没有编辑知识库文章的权限"
        subTitle={
          editingId
            ? '修改文章需要「编辑文章」和「查看文章详情」权限。'
            : '新建文章需要「新建文章」权限。'
        }
        extra={<Button onClick={() => navigate(MANAGE_PATH)}>返回知识管理</Button>}
      />
    )
  }

  return <ArticleEditor editingId={editingId} />
}

function ArticleEditor({ editingId }: { editingId: string | null }) {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const [form] = Form.useForm<ArticleFormValues>()
  const content = Form.useWatch('content', form)

  const [categories, setCategories] = useState<KnowledgeCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [activeTab, setActiveTab] = useState('edit')
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const list = await listKnowledgeCategories()
        if (!cancelled) {
          setCategories(list)
        }
      } catch (error) {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '加载分类失败')
        }
      } finally {
        if (!cancelled) {
          setCategoriesLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [message])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        if (editingId) {
          const detail = await getManageKnowledge(editingId)
          if (cancelled) {
            return
          }
          form.setFieldsValue({
            title: detail.title,
            summary: detail.summary ?? '',
            content: detail.content,
            categoryId: detail.categoryId,
            status: detail.status,
          })
        } else {
          form.resetFields()
          form.setFieldsValue({ status: 'DRAFT' })
        }
      } catch (error) {
        if (!cancelled) {
          setLoadError(true)
          message.error(error instanceof Error ? error.message : '加载文章失败')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [editingId, form, message])

  useEffect(() => {
    if (!dirty) {
      return
    }
    // 只拦得住刷新和关闭标签页；侧边栏跳转没有拦截能力（useRoutes 没有 useBlocker），
    // 所以离开页面的按钮另外做确认。
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

  const leave = () => {
    if (!dirty) {
      navigate(MANAGE_PATH)
      return
    }
    modal.confirm({
      title: '放弃未保存的内容？',
      content: '离开后本次修改不会保留。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => navigate(MANAGE_PATH),
    })
  }

  const onSubmit = async () => {
    let values: ArticleFormValues
    try {
      values = await form.validateFields()
    } catch {
      // 校验失败时 antd 已经在字段上给出提示，这里不需要额外消息
      return
    }

    setSubmitting(true)
    try {
      const payload: SaveKnowledgeArticlePayload = {
        title: values.title.trim(),
        summary: values.summary?.trim() || undefined,
        content: values.content,
        categoryId: values.categoryId,
        status: values.status,
      }
      if (editingId) {
        await updateKnowledgeArticle(editingId, payload)
        message.success(
          payload.status === 'PUBLISHED' ? '保存成功，正在后台更新索引' : '保存成功',
        )
      } else {
        await createKnowledgeArticle(payload)
        message.success(
          payload.status === 'PUBLISHED' ? '创建成功，正在后台建立索引' : '创建成功',
        )
      }
      setDirty(false)
      navigate(MANAGE_PATH)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  if (loadError) {
    return (
      <Result
        status="error"
        title="加载文章失败"
        subTitle="文章可能已被删除，或你没有查看详情的权限。"
        extra={<Button onClick={() => navigate(MANAGE_PATH)}>返回知识管理</Button>}
      />
    )
  }

  return (
    <Card
      title={editingId ? '编辑文章' : '新建文章'}
      extra={
        <Space>
          <Button onClick={leave}>取消</Button>
          <Button
            type="primary"
            loading={submitting}
            disabled={loading}
            onClick={() => void onSubmit()}
          >
            {editingId ? '保存' : '创建'}
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Form
          form={form}
          layout="vertical"
          onValuesChange={() => setDirty(true)}
        >
          <Row gutter={24}>
            <Col xs={24} lg={8} xl={7}>
              <Card size="small" title="文章信息">
                <Form.Item
                  name="categoryId"
                  label="分类"
                  rules={[{ required: true, message: '请选择分类' }]}
                >
                  <Select
                    placeholder="选择分类"
                    loading={categoriesLoading}
                    options={categories.map((category) => ({
                      value: category.id,
                      label: category.name,
                    }))}
                  />
                </Form.Item>
                <Form.Item
                  name="summary"
                  label="摘要"
                  rules={[{ max: 500, message: '最长 500 个字符' }]}
                  extra="显示在列表页，不填也可以"
                >
                  <Input.TextArea
                    rows={5}
                    maxLength={500}
                    showCount
                    placeholder="一句话说明这篇经验解决什么问题"
                  />
                </Form.Item>
                <Form.Item
                  name="status"
                  label="状态"
                  extra="发布后会异步建立语义索引，几秒钟内语义搜索可能还搜不到"
                >
                  <Segmented block options={STATUS_OPTIONS} />
                </Form.Item>
              </Card>
            </Col>

            <Col xs={24} lg={16} xl={17}>
              <Form.Item
                name="title"
                label="标题"
                rules={[
                  { required: true, message: '请输入标题' },
                  { max: 200, message: '最长 200 个字符' },
                ]}
              >
                <Input
                  size="large"
                  maxLength={200}
                  showCount
                  placeholder="例如：打印机频繁卡纸的处理步骤"
                />
              </Form.Item>

              <Tabs
                activeKey={activeTab}
                onChange={setActiveTab}
                items={[
                  {
                    key: 'edit',
                    label: (
                      <Space size={4}>
                        <EditOutlined />
                        编辑
                      </Space>
                    ),
                    children: (
                      <Form.Item
                        name="content"
                        label="正文（Markdown）"
                        rules={[
                          { required: true, message: '请输入正文' },
                          { max: 100000, message: '最长 100000 个字符' },
                        ]}
                      >
                        <Input.TextArea
                          rows={26}
                          style={{
                            fontFamily:
                              "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace",
                          }}
                          placeholder={'# 现象\n\n...\n\n## 处理步骤\n\n1. ...'}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    key: 'preview',
                    label: (
                      <Space size={4}>
                        <EyeOutlined />
                        预览
                      </Space>
                    ),
                    children: content?.trim() ? (
                      <KnowledgeMarkdown content={content} />
                    ) : (
                      <Empty description="还没有内容" />
                    ),
                  },
                ]}
              />
            </Col>
          </Row>
        </Form>
      </Spin>
    </Card>
  )
}
