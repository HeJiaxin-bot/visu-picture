import { saveAs } from 'file-saver'

/**
 * 从路由 query 之类的「可能是数组」的值中取出第一个标量，保持字符串形态
 *
 * ⚠️ 与 toNumberOrUndefined 的区别至关重要：
 * 后端 JsonConfig 把 Long 序列化为 String，是因为雪花 id（如 2100942816594628609）
 * 超出 Number.MAX_SAFE_INTEGER。对它调用 Number() 会得到
 * 2100942816594628600 —— 末位被悄悄改写，请求随即 404。
 * 因此**所有实体 id 都必须以字符串形式在前后端之间传递**，绝不做数值转换。
 *
 * @returns 非空字符串，或 undefined（表示未提供）
 */
export function firstQueryValue(raw: unknown): string | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw
  if (value == null) return undefined
  const str = String(value).trim()
  return str === '' ? undefined : str
}

/**
 * 把路由 query / 表单输入得到的值安全地转成数字
 *
 * 仅用于**真正的数值型参数**（页码、分页大小、空间额度等），
 * 不要用于实体 id —— 见 firstQueryValue 的说明。
 *
 * @param raw   原始值（可能是字符串、数字、数组或空）
 * @param min   允许的最小值（默认 1；传 0 表示允许 0）
 * @returns 合法数字，或 undefined（表示"未提供/不合法"）
 */
export function toNumberOrUndefined(
  raw: unknown,
  min = 1,
): number | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw
  if (value == null || value === '') return undefined
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return undefined
  return parsed >= min ? parsed : undefined
}

/**
 * 格式化文件大小
 * @param size
 */
export const formatSize = (size?: number) => {
  if (!size) return '未知'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

/**
 * 下载图片
 * @param url 图片下载地址
 * @param fileName 要保存为的文件名
 */
export function downloadImage(url?: string, fileName?: string) {
  if (!url) {
    return
  }
  saveAs(url, fileName)
}

/**
 * 将颜色值转换为标准 #RRGGBB 格式
 * @param input
 */
export function toHexColor(input: string) {
  // 去掉 0x 前缀
  const colorValue = input.startsWith('0x') ? input.slice(2) : input

  // 将剩余部分解析为十六进制数，再转成 6 位十六进制字符串
  const hexColor = parseInt(colorValue, 16).toString(16).padStart(6, '0')

  // 返回标准 #RRGGBB 格式
  return `#${hexColor}`
}

