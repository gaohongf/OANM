import type { DownloadResult } from '../../api/software'

/**
 * 触发一次同源下载。
 *
 * `download` 属性只对同源地址生效，所以后端把 url 收紧成了 `/file/` 开头
 * （见 `SaveSoftwareVersionCommand.validateFileUrl`）；这里不再做第二套判断。
 * 用 `<a>` 而不是 `window.open`，是为了让浏览器按 `fileName` 保存，
 * 而不是用存储平台生成的那串随机文件名。
 */
export function triggerDownload({ url, fileName }: DownloadResult): void {
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}
