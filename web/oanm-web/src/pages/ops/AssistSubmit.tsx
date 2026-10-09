import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  Progress,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { ThunderboltOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router'

import { AI_PERMISSIONS, startAssistSubmit } from '../../api/ai'
import type { AssistLine } from '../../api/ai'
import { clearDraft, draftInputRounds, loadDraft, matchesConversation } from '../../api/aiSession'
import { createWorkOrder, PRIORITY_OPTIONS, splitRounds, TYPE_OPTIONS } from '../../api/workOrder'
import type { SubmitWorkOrderPayload, WorkOrderPriority, WorkOrderType } from '../../api/workOrder'
import { Auth } from '../../permission/Auth'
import { RawOutputPanel } from './components/RawOutputPanel'

const { Text, Paragraph } = Typography
const { TextArea } = Input

// Select 只认 {value,label}，我们自己那份元数据还带着颜色，这里摘一下
const TYPE_SELECT_OPTIONS = TYPE_OPTIONS.map((item) => ({ value: item.value, label: item.label }))
const PRIORITY_SELECT_OPTIONS = PRIORITY_OPTIONS.map((item) => ({ value: item.value, label: item.label }))

/**
 * AI 按约定要填的 5 个字段（模型给的 `content` / `solution_detail` 会映射成后两个）。
 * "填到哪儿了"的进度按它算 —— 分母是**约定**而不是"模型已经说了几行"，
 * 否则进度条永远显示 100%，等于没有进度。
 */
const AI_FIELDS = ['title', 'type', 'priority', 'problemDescription', 'solutionDetail'] as const
type AiField = (typeof AI_FIELDS)[number]

/**
 * 字段标签 + AI 刚写入的标记。
 *
 * 状态挂在**标签**上而不是字段内部：`Input`、`TextArea`、`Select` 各自的 API 不一样
 * （`suffix` 有的支持有的不支持），而标签是三者共有的，一套视觉就能覆盖全部字段。
 *
 * 挂在标签上还有一个原因：标签就在用户视线落点上。只放在页面顶部的横幅里的话，
 * 表单长一点就滚出视野了，而"AI 正在往下填"这件事恰恰要在字段那里才看得见。
 */
function FieldLabel({ text, active }: { text: string; active: boolean }) {
  return (
    <Space size={6}>
      <span>{text}</span>
      {active && <Tag color="processing">AI 刚填入</Tag>}
    </Space>
  )
}

interface AssistFormValues {
  title: string
  type: WorkOrderType
  priority: WorkOrderPriority
  problemDescription: string
  solutionDetail?: string
  originalProblemDescription?: string
}

/**
 * 智能提单第二步：AI 辅助填单。
 *
 * ## 打开就先有一张单
 * 表单立刻渲染出来（哪怕字段还都是空的），然后每收到一行 SSE 输出就 `setFieldValue`
 * 填一个字段 —— 用户看到的是"单子被一项项填上"，而不是"转圈然后整张表突然出现"。
 *
 * ## 填写期间整张表单是锁住的
 * 光把字段变灰不够：变灰看着仍然像"能改"，而用户只要在模型回来之前敲进去几个字，
 * 后面那一格被 `setFieldValue` 写上时是**整段覆盖**，敲的字真会丢。所以锁做两道 ——
 * `Form` 级 `disabled`（一处声明盖住表单里每个控件，以后新加的字段也自动被盖上）
 * 加上 `Spin` 的 nest 模式（容器 `pointer-events: none`，指示器那层还会吃掉点击）。
 *
 * ## 为什么是个大转圈，而不是一行字
 * 文字用户不读，得有个"在动"的东西交代状态：`Spin` 的指示器与 `description`
 * 居中浮在表单上，是这一页最显眼的动效。详细的说明（模型要几秒到十几秒才吐第一行、
 * 想自己填怎么退出）留在上面的 Alert 里 —— 它在遮罩之外，始终清晰可读。
 *
 * ## 提交要带上 AI 会话 id
 * 工单里存的是**指针**而不是对话副本：`aiConversationId` 指向 service-ai 的
 * `spring_ai_chat_memory`，拿它能把整段对话捞回来（SQL 见后端 `db/schema.sql`）。
 * 另外还存一条 `aiSelectedOptionId`：光有会话 id 只知道是哪次对话，
 * 不知道用户当时认可的是哪一条推测。
 *
 * ## 「原始描述」以本地那份为准
 * 它是用户自己敲的原话，模型转述会改写、会丢信息。所以模型的 `user_input_N`
 * **只用来补本地缺失的轮次**（刷新后草稿丢了、或者直接拿链接打开这一页时）。
 *
 * ## 没有 conversationId 时是手工建单模式
 * 不发起任何请求，空表照常能提交 —— 让这一页也能当"手工建单"用，
 * 而不是变成一个"必须从第一步走过来"的死页面。
 */
export default function AssistSubmit() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form] = Form.useForm<AssistFormValues>()

  const conversationId = searchParams.get('conversationId')
  const optionRaw = searchParams.get('option')
  const parsedOption = optionRaw === null ? Number.NaN : Number(optionRaw)
  const option = Number.isInteger(parsedOption) ? parsedOption : null
  // 两个参数缺一个就没法调那个接口（userSelect 是必填的）
  // 草稿只在挂载时读一次：它是"进来时的已知信息"，之后表单自己就是真相
  const [draft] = useState(() => loadDraft())

  /**
   * 真正要用的会话 id 与选项：**地址栏里的优先，缺的从草稿里补**。
   *
   * 为什么必须补：这一页在左侧菜单里有一条（「AI 辅助填单」），用户从菜单点进来时
   * 地址栏是干净的。而它需要的两样东西 —— 会话 id 和用户选中的选项 —— 草稿里正好
   * 都留着（那正是上一步写进 sessionStorage 的）。不补的话，用户点了一个叫
   * "AI 辅助填单"的菜单，得到的却是一张要自己填的空表，而且**一点反应都没有**：
   * 他会以为功能坏了。
   */
  const draftOptionId = draft?.selected?.option.id
  const activeConversationId = conversationId ?? draft?.conversationId ?? null
  const activeOption = option ?? (typeof draftOptionId === 'number' ? draftOptionId : null)
  const canStream = activeConversationId !== null && activeOption !== null
  // 草稿属于别的会话时不能采用 —— 那可能是上一次提单留下的
  const ownDraft = matchesConversation(draft, activeConversationId) ? draft : null

  const [streaming, setStreaming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  /** AI 已经填进去的字段。用来算"填到哪儿了"，也让空字段能说出"我这就来" */
  const [filledFields, setFilledFields] = useState<AiField[]>([])
  /** 最近一条写入落在哪个字段上：标记跟着它走，AI 往下一格填的效果就是这么来的 */
  const [lastFilled, setLastFilled] = useState<AiField | null>(null)
  /** 是用户自己按了「中断」还是流正常结束 —— 横幅上那句话说的是哪一句，取决于它 */
  const [interrupted, setInterrupted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [noContent, setNoContent] = useState(false)
  const [unrecognized, setUnrecognized] = useState<{ key: string; raw: string }[]>([])
  const [rawLines, setRawLines] = useState<string[]>([])
  const abortRef = useRef<AbortController | null>(null)

  // 用 useCallback 固定身份：它被 startStream 依赖，而 startStream 又是挂载 effect 的依赖 ——
  // 每渲染换一个新函数就等于每渲染重启一次流
  const applyLine = useCallback((line: AssistLine) => {
    // 记一笔"这一项是 AI 填的"：进度条靠它，字段上的标记也靠它
    const markFilled = (name: AiField) => {
      setFilledFields((prev) => (prev.includes(name) ? prev : [...prev, name]))
      setLastFilled(name)
    }
    switch (line.kind) {
      case 'field':
        form.setFieldValue(line.field, line.value)
        markFilled(line.field)
        return
      case 'type':
      case 'priority':
        form.setFieldValue(line.kind, line.value)
        markFilled(line.kind)
        return
      case 'round': {
        // 本地已有的轮次一律不动（用户原话为权威），只补缺的
        const current = splitRounds(form.getFieldValue('originalProblemDescription'))
        if (line.index < current.length) {
          return
        }
        const next = [...current]
        while (next.length < line.index) {
          next.push('')
        }
        next[line.index] = line.value
        form.setFieldValue('originalProblemDescription', next.join('\n'))
        return
      }
      case 'userChoose':
        // 勾选是表单自己提交的（来自地址栏的 option），不信模型回显的那个编号 ——
        // 它只是个"我收到了"的应答，万一记错了会话反而会把归档的选项号写歪
        return
      case 'unrecognized':
        setUnrecognized((prev) => [...prev, { key: line.key, raw: line.raw }])
        return
    }
    // form 实例由 useForm 提供，跨渲染稳定，所以这个 callback 实际上只创建一次
  }, [form])

  /**
   * 发起一次 AI 填写。
   *
   * 抽成函数是因为它有两个入口（挂载时自动跑一次 / 用户点「让 AI 重新填写」），
   * 而这两个入口必须完全一致 —— 复制一份出来迟早会只改其中一处。
   *
   * @returns 中断用的 controller；没有会话信息时返回 undefined（不发请求）
   */
  const startStream = useCallback(() => {
    if (activeConversationId === null || activeOption === null) {
      return undefined
    }
    const controller = new AbortController()
    abortRef.current = controller
    let received = 0

    setStreaming(true)
    setUnrecognized([])
    setRawLines([])
    setError(null)
    setNoContent(false)
    setFilledFields([])
    setLastFilled(null)
    setInterrupted(false)

    void startAssistSubmit({
      conversationId: activeConversationId,
      userSelect: activeOption,
      signal: controller.signal,
      onRawLine: (line) => setRawLines((prev) => [...prev, line]),
      onLine: (line) => {
        received += 1
        applyLine(line)
      },
      onError: (msg) => setError(msg),
    }).finally(() => {
      // 只有**当前这条流**才有资格收尾。
      //
      // 这不是理论风险：dev 下 `<StrictMode>` 会把挂载 effect 跑两遍（挂载 → 清理 →
      // 再挂载），第一次的流在清理时当场被 abort，而它的 finally 是异步落地的 ——
      // 那记 setStreaming(false) 会砸在**第二次**正在跑的流头上。表现正是"AI 在填、
      // 表单却是可改的"：用户敲进去的字随后被 setFieldValue 整段覆盖，而顶上
      // "AI 正在填写"的进度也一并消失，只剩一句收尾文字。按 controller 认人之后，
      // 被中断的那条流安静退场，一个状态字段都不碰
      if (abortRef.current !== controller) {
        return
      }
      abortRef.current = null
      setStreaming(false)
      // 用户自己中断的不算"AI 没返回内容"
      setInterrupted(controller.signal.aborted)
      setNoContent(!controller.signal.aborted && received === 0)
    })

    return controller
  }, [activeConversationId, activeOption, applyLine])

  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect -- 起流就是要立刻进入 streaming 态
    const controller = startStream()
    // 卸载时掐掉流：否则它会继续读到模型说完，期间还在往已卸载的组件写状态
    return () => controller?.abort()
  }, [startStream])

  const onInterrupt = () => {
    abortRef.current?.abort()
    message.info('已中断 AI 填写，已填好的内容会保留')
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    setSubmitting(true)
    try {
      const payload: SubmitWorkOrderPayload = {
        title: values.title,
        type: values.type,
        priority: values.priority,
        problemDescription: values.problemDescription,
        originalProblemDescription: values.originalProblemDescription,
        solutionDetail: values.solutionDetail,
        // 归档指针与选中的选项：手工建单时为 undefined，后端存 null
        aiConversationId: activeConversationId ?? undefined,
        aiSelectedOptionId: activeOption ?? undefined,
      }
      await createWorkOrder(payload)
      // 单子已经落库，草稿的使命结束 —— 留着的话下次进这一页又会看到上一单的内容
      clearDraft()
      message.success('工单已提交')
      navigate('/ops/work-order')
    } catch (err) {
      // 失败时**不清空任何已填内容**：让用户能改了再交
      message.error(err instanceof Error ? err.message : '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  const disabled = streaming
  const filledCount = filledFields.length
  // 还没轮到填的字段：框里写明"AI 正在生成"，而不是空着一个看起来像坏了的输入框
  const placeholderFor = (name: AiField, normal: string) =>
    streaming && !filledFields.includes(name) ? 'AI 正在生成…' : normal

  return (
    <Card
      className="page-transition"
      title="AI 辅助填单"
      extra={
        streaming ? (
          <Button danger onClick={onInterrupt}>
            中断
          </Button>
        ) : null
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {canStream ? (
          <Alert
            type="info"
            showIcon
            icon={streaming ? <Spin size="small" /> : undefined}
            message={
              // 四种收尾各有各的说法：中断、失败、正常填完、什么都没填出来。
              // 统一说成"已完成"会在这几种情况下撒谎，而用户这时候正盯着它看
              streaming
                ? 'AI 正在填写，请稍候…'
                : interrupted
                  ? '已中断 AI 填写，已填好的内容会保留'
                  : error !== null
                    ? 'AI 填写失败，已填的内容会保留'
                    : filledCount > 0
                      ? 'AI 已完成填写，可以修改后提交'
                      : 'AI 没有填出内容，请手工填写'
            }
            description={
              <Space direction="vertical" size={2} style={{ width: '100%' }}>
                {streaming && (
                  // 逐行返回、几秒一行，所以必须有个"在动"的东西交代它没卡住：
                  // 进度按约定要填的 5 项算（见 AI_FIELDS），填几项就跳几格。
                  // 第一行之前的等待最长（实测这几秒到十几秒都有可能），那时进度条还是空的，
                  // 只靠一个 0% 的空条会像卡住，所以把它的话改成"请求中…"
                  <Progress
                    percent={Math.round((filledCount / AI_FIELDS.length) * 100)}
                    size="small"
                    status="active"
                    format={() =>
                      filledCount === 0 ? '等待模型返回第一行…' : `${filledCount}/${AI_FIELDS.length} 项`
                    }
                    style={{ maxWidth: 320 }}
                  />
                )}
                {streaming && (
                  <Text type="secondary">
                    模型要先想几秒到十几秒才吐第一行，之后一行一行回来，中间也会有停顿，请等它填完；
                    填写期间表单不可编辑，想自己填请点右上角「中断」。
                  </Text>
                )}
                <Text type="secondary">
                  归档用会话 id：
                  <Text code copyable={{ text: activeConversationId ?? '' }}>
                    {activeConversationId}
                  </Text>
                  {activeOption !== null && <> ｜ 选中选项 #{activeOption}</>}
                </Text>
                <Text type="secondary">
                  提交后这条对话可以在 spring_ai_chat_memory 里按这个 id 查到。
                </Text>
              </Space>
            }
          />
        ) : (
          // 没有会话就是没有 AI —— 这一页叫「AI 辅助填单」，但菜单直接点进来时地址栏里
          // 没有参数、草稿里也没有上一次的会话，此时**不该装作在等 AI**：
          // 明说为什么，并把去第一步的路放在手边（一个按钮，不是一句"请先…"）。
          <Alert
            type="warning"
            showIcon
            message="这一页还没有 AI 会话，所以不会自动填"
            description={
              <Space direction="vertical" size="small">
                <Text type="secondary">
                  AI 是根据你在「智能提单」页描述的问题来填这张单的。现在没有那段对话，
                  可以直接手工填写提交，也可以先去让 AI 理解一下你的问题。
                </Text>
                <Auth
                  code={AI_PERMISSIONS.intentInference}
                  fallback={<Text type="secondary">没有「智能提单」权限，请联系管理员</Text>}
                >
                  <Button
                    type="primary"
                    icon={<ThunderboltOutlined />}
                    onClick={() => navigate('/ops/ai/intent')}
                  >
                    去智能提单，让 AI 先理解我的问题
                  </Button>
                </Auth>
              </Space>
            }
          />
        )}

        {/* 遮罩只罩表单：右上角的「中断」在卡片头上、不在这一层里，填写期间仍然可点 ——
            锁死输入不等于把用户关在里面，他随时能退出，退出的代价只是"后面的字段不填了" */}
        <Spin
          spinning={streaming}
          size="large"
          description={<Text strong>AI 正在填写这张工单，请稍候</Text>}
        >
          <Form<AssistFormValues>
            form={form}
            layout="vertical"
            // 两道锁（字段上还各写了一遍，见下面每个 disabled）：这里一处声明就盖住
            // 表单里每个控件，字段级那份是防哪天换成非 antd 控件、Form 级失效而静默开门。
            // 锁失效的代价是用户敲的字被 AI 覆盖掉 —— 不值得为少写几行去赌
            disabled={disabled}
            // 用户原话（一行一轮）先填进去，AI 的 user_input_N 只补本地缺的轮次
            initialValues={{ originalProblemDescription: draftInputRounds(ownDraft).join('\n') }}
          >
            <Form.Item
              name="title"
              label={<FieldLabel text="标题" active={streaming && lastFilled === 'title'} />}
              rules={[{ required: true, message: '请填写标题' }, { max: 200, message: '最长 200 个字符' }]}
            >
              <Input
                placeholder={placeholderFor('title', '一句话概括这个工单')}
                disabled={disabled}
                maxLength={200}
              />
            </Form.Item>

            <Form.Item
              name="type"
              label={<FieldLabel text="类型" active={streaming && lastFilled === 'type'} />}
              rules={[{ required: true, message: '请选择类型' }]}
            >
              <Select
                options={TYPE_SELECT_OPTIONS}
                placeholder={placeholderFor('type', '需求还是故障')}
                disabled={disabled}
              />
            </Form.Item>

            <Form.Item
              name="priority"
              label={<FieldLabel text="优先级" active={streaming && lastFilled === 'priority'} />}
              rules={[{ required: true, message: '请选择优先级' }]}
            >
              <Select
                options={PRIORITY_SELECT_OPTIONS}
                placeholder={placeholderFor('priority', '紧急程度')}
                disabled={disabled}
              />
            </Form.Item>

            <Form.Item
              name="problemDescription"
              label={
                <FieldLabel text="工单描述" active={streaming && lastFilled === 'problemDescription'} />
              }
              rules={[
                { required: true, message: '请填写工单描述' },
                { max: 2000, message: '最长 2000 个字符' },
              ]}
            >
              <TextArea
                rows={5}
                disabled={disabled}
                maxLength={2000}
                showCount
                placeholder={placeholderFor('problemDescription', '经过整理的问题描述')}
              />
            </Form.Item>

            <Form.Item
              name="originalProblemDescription"
              label="原始描述"
              extra="一行一轮：用户在智能提单页每次补充的内容都单独一行。可以直接编辑增删。"
            >
              <TextArea rows={4} disabled={disabled} placeholder="用户最初是怎么说的" />
            </Form.Item>

            <Form.Item
              name="solutionDetail"
              label={<FieldLabel text="解决方法" active={streaming && lastFilled === 'solutionDetail'} />}
            >
              <TextArea
                rows={8}
                disabled={disabled}
                placeholder={placeholderFor('solutionDetail', 'AI 给出的解决步骤，提交前可以再改')}
              />
            </Form.Item>
          </Form>
        </Spin>

        {unrecognized.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message="有字段没能识别，需要手动选择"
            description={
              <Space direction="vertical" size={2}>
                {unrecognized.map((item, index) => (
                  <Text key={`${item.key}-${index}`}>
                    字段 <Text code>{item.key}</Text> 的值「{item.raw}」不是合法取值，请在上面的下拉框里手动选择。
                  </Text>
                ))}
              </Space>
            }
          />
        )}

        {error !== null && <Alert type="error" showIcon message="AI 填写失败" description={error} />}

        {noContent && (
          <Alert
            type="warning"
            showIcon
            message="AI 没有返回任何内容"
            description="可以展开下面的「原始输出」看看它到底返回了什么，然后手工填写。"
          />
        )}

        <Paragraph style={{ margin: 0 }}>
          <Text type="secondary">
            提交后工单会落到 service-work-order，并带上这里的会话 id 便于日后回溯这次对话。
          </Text>
        </Paragraph>

        <Space>
          <Button type="primary" loading={submitting} disabled={streaming} onClick={() => void onSubmit()}>
            提交工单
          </Button>
          {canStream && !streaming && (
            // 重新拉一次 AI 的填写结果。已经手改过的字段会被覆盖 —— 这是"重来"的语义，
            // 所以按钮文字里说清"重新"，而不是含糊的"刷新"
            <Button onClick={() => startStream()}>让 AI 重新填写</Button>
          )}
        </Space>

        <RawOutputPanel lines={rawLines} />
      </Space>
    </Card>
  )
}
