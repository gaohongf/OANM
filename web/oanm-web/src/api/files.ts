import { ApiError, request } from './http'

/**
 * 文件上传。
 *
 * 后端有两个上传入口，权限和上限是绑在一起的：
 * - 图片接口 `/api/files/upload/image`：登录即可（后端 `@LoginOnly`），单张 10MB，
 *   只收常见图片格式。提单截图这类场景都走它。
 * - 大文件接口 `/api/files/upload/large`：需要 `POST:/api/files/upload/large` 权限
 *   （管理员 / 运维），单个 1GB，不限类型。上传软件安装包时显式传 `kind: 'large'`。
 *
 * 读取不需要经过这里：返回的 `url` 是相对地址，浏览器直接按网关同源访问
 * （网关有 `/file/**` 路由）。
 */

export const FILE_IMAGE_UPLOAD_PATH = '/api/files/upload/image'
export const FILE_LARGE_UPLOAD_PATH = '/api/files/upload/large'

/** 图片接口的单文件上限，与后端 `UploadLimits.IMAGE_MAX_BYTES` 一致。 */
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024

/** 大文件接口的单文件上限，与后端 `UploadLimits.LARGE_MAX_BYTES` 一致。 */
export const LARGE_MAX_BYTES = 1024 * 1024 * 1024

/** 走哪个上传入口 */
export type UploadKind = 'image' | 'large'

/** 上传成功后拿到的文件信息。字段与后端 `FileUploadRes` 一一对应。 */
export interface UploadedFile {
  id: string
  /** 相对访问地址，如 `/file/2026/10/19/xxx.jpg` */
  url: string
  /** 存储平台生成的文件名 */
  filename: string
  /** 用户选文件时的原始文件名 */
  originalFilename: string
  size: number
  contentType: string
}

export interface UploadFileOptions {
  /** 默认 `image`（登录即可）；上传安装包等大文件时传 `large`（需要权限） */
  kind?: UploadKind
  /** 文件所属对象类型，比如 `AI_INTENT` */
  objectType?: string
  /** 文件所属对象 id；首轮对话还没有 conversationId 时可不传 */
  objectId?: string
  signal?: AbortSignal
}

/**
 * 上传一个文件。
 *
 * 表单字段名必须是 `file` —— 后端用 `@RequestPart("file")` 接收，名字对不上会直接 400。
 * 响应里的可选字段做了兜底：`url` 是唯一必须有的，其余缺了就用本地文件的信息补上，
 * 界面照样能显示缩略图。
 */
export async function uploadFile(
  file: File,
  options: UploadFileOptions = {},
): Promise<UploadedFile> {
  const path = options.kind === 'large' ? FILE_LARGE_UPLOAD_PATH : FILE_IMAGE_UPLOAD_PATH
  const form = new FormData()
  form.append('file', file)

  const raw = await request<Partial<UploadedFile> | null>(path, {
    method: 'POST',
    body: form,
    query: {
      objectType: options.objectType,
      objectId: options.objectId,
    },
    signal: options.signal,
  })

  if (raw === null || typeof raw.url !== 'string' || raw.url === '') {
    // 后端成功却给不出访问地址：这张图既显示不了也存不进草稿，按失败处理
    throw new ApiError('上传成功但未返回文件地址', -1, 200)
  }

  return {
    id: typeof raw.id === 'string' ? raw.id : '',
    url: raw.url,
    filename: typeof raw.filename === 'string' ? raw.filename : '',
    originalFilename: raw.originalFilename || file.name,
    size: typeof raw.size === 'number' ? raw.size : file.size,
    contentType: raw.contentType || file.type,
  }
}
