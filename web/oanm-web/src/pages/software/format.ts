/**
 * 文件大小格式化。
 *
 * 用 1024 进制并保留一位小数：驱动和安装包动辄几百 MB，
 * 显示成 "382.4 MB" 比 "382417920 字节" 直观，也比精确到字节更稳定。
 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes < 0) {
    return '-'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(1)} ${units[unit]}`
}
