import { useEffect, useState } from 'react'
import { App as AntdApp, Button, Form, Input, Modal, Select, Space, Typography, Upload } from 'antd'
import { UploadOutlined } from '@ant-design/icons'

import { LARGE_MAX_BYTES, uploadFile } from '../../../api/files'
import type { UploadedFile } from '../../../api/files'
import {
  createSoftwareVersion,
  PLATFORM_OPTIONS,
  updateSoftwareVersion,
} from '../../../api/software'
import type {
  SaveSoftwareVersionPayload,
  SoftwareManageVersion,
  SoftwarePlatform,
} from '../../../api/software'
import { formatFileSize } from '../format'

const { Text } = Typography

const PLATFORM_SELECT_OPTIONS = PLATFORM_OPTIONS.map((option) => ({
  value: option.value,
  label: option.label,
}))

interface VersionFormValues {
  versionNo: string
  platform: SoftwarePlatform
  releaseNote?: string
}

interface VersionFormModalProps {
  open: boolean
  /** 新版本挂到哪个软件下 */
  softwareId: string | null
  /** null = 新增；非 null = 编辑 */
  editing: SoftwareManageVersion | null
  onCancel: () => void
  onSuccess: () => void
}

/**
 * 新增 / 编辑版本。
 *
 * ## 编辑时文件是可选的"一整组"
 * 不重新上传就一个文件字段都不发，后端保留原文件；上传了新文件就整组替换。
 * 后端会再校验一次（只传一部分 → `SOFTWARE_FILE_INVALID`），前端这里只是提前把路径走顺。
 *
 * ## 上传没有进度条
 * `uploadFile` 走的是 fetch，拿不到上传进度；上传期间禁用提交并显示 loading，
 * 避免用户以为没反应而重复点击。
 */
export function VersionFormModal({
  open,
  softwareId,
  editing,
  onCancel,
  onSuccess,
}: VersionFormModalProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<VersionFormValues>()
  const [uploading, setUploading] = useState(false)
  const [uploaded, setUploaded] = useState<UploadedFile | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    // 打开/切换版本时清掉上一次选的本地文件，避免把 A 的文件提交到 B 上
    // eslint-disable-next-line react/set-state-in-effect
    setUploaded(null)
    form.setFieldsValue({
      versionNo: editing?.versionNo ?? '',
      platform: editing?.platform ?? 'WINDOWS',
      releaseNote: editing?.releaseNote ?? undefined,
    })
  }, [open, editing, form])

  const onPickFile = async (file: File) => {
    if (!softwareId) {
      return
    }
    // 安装包走大文件接口（需要权限）；这里先拦一次，让用户立刻看到"文件太大"，
    // 而不是等上传到一半才被拒绝。
    if (file.size > LARGE_MAX_BYTES) {
      message.error('文件不能超过 1GB')
      return
    }
    setUploading(true)
    try {
      const result = await uploadFile(file, {
        kind: 'large',
        objectType: 'SOFTWARE_VERSION',
        objectId: softwareId,
      })
      setUploaded(result)
      message.success(`已上传 ${result.originalFilename}`)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const onSubmit = async () => {
    const values = await form.validateFields()
    if (!softwareId) {
      return
    }
    if (!editing && !uploaded) {
      message.warning('请先上传安装文件')
      return
    }
    setSubmitting(true)
    try {
      const payload: SaveSoftwareVersionPayload = {
        versionNo: values.versionNo.trim(),
        platform: values.platform,
        releaseNote: values.releaseNote?.trim() || undefined,
      }
      if (uploaded) {
        payload.fileId = uploaded.id
        payload.fileUrl = uploaded.url
        payload.fileName = uploaded.originalFilename
        payload.fileSize = uploaded.size
      }
      if (editing) {
        await updateSoftwareVersion(editing.id, payload)
        message.success('修改成功')
      } else {
        await createSoftwareVersion(softwareId, payload)
        message.success('创建成功')
      }
      onSuccess()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }

  // 新增时还没有文件；编辑时可以保留原文件
  const currentFileName = uploaded?.originalFilename ?? editing?.fileName ?? null
  const currentFileSize = uploaded?.size ?? editing?.fileSize ?? null

  return (
    <Modal
      title={editing ? '编辑版本' : '新增版本'}
      open={open}
      onOk={() => void onSubmit()}
      confirmLoading={submitting}
      okButtonProps={{ disabled: uploading }}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="versionNo"
          label="版本号"
          rules={[
            { required: true, message: '请输入版本号' },
            { max: 50, message: '最长 50 个字符' },
          ]}
        >
          <Input placeholder="例如：1.2.3" />
        </Form.Item>
        <Form.Item
          name="platform"
          label="适用平台"
          rules={[{ required: true, message: '请选择平台' }]}
        >
          <Select options={PLATFORM_SELECT_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="releaseNote"
          label="版本说明"
          rules={[{ max: 4000, message: '最长 4000 个字符' }]}
        >
          <Input.TextArea rows={4} placeholder="这一版更新了什么、修复了哪些问题" />
        </Form.Item>
        <Form.Item label="安装文件">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {currentFileName ? (
              <Text type="secondary">
                当前文件：{currentFileName}（{formatFileSize(currentFileSize)}）
                {editing ? '，不重新上传则保留' : ''}
              </Text>
            ) : (
              <Text type="secondary">还没有上传文件</Text>
            )}
            <Upload
              showUploadList={false}
              disabled={uploading}
              beforeUpload={(file) => {
                // 返回 false 阻止 antd 自己发请求：上传走我们自己的 uploadFile（要带 token 和 objectType）
                void onPickFile(file)
                return false
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>
                {currentFileName ? '重新上传' : '选择文件'}
              </Button>
            </Upload>
            <Text type="secondary">
              单个文件不超过 1GB；上传安装包需要「上传大文件」权限
            </Text>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  )
}
