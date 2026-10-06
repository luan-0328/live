export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
export function imageError(file) {
  if (!IMAGE_TYPES.includes(file.type)) return '仅支持 JPG/PNG/GIF/WebP 图片'
  if (!file.size || file.size > 5 * 1024 * 1024) return '图片不能为空，且不能超过5MB'
  return ''
}
