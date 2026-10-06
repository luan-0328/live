// 时间格式化工具：相对时间 → 绝对日期
export function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 259200000) return Math.floor(diff / 86400000) + '天前'
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  if (y === now.getFullYear()) return mo + '-' + da
  return y + '-' + mo + '-' + da
}
