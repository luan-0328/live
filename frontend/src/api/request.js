import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession, rotateToken } from '../utils/session'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    config.sessionToken = token
  }
  if (import.meta.env.DEV) console.log('[API]', config.method?.toUpperCase(), config.baseURL + config.url)
  return config
})

request.interceptors.response.use(
  res => {
    // 刷新 token 续期：如果后端返回了 X-Auth-Token，更新本地存储
    const newToken = res.headers['x-auth-token']
    if (newToken) rotateToken(res.config.sessionToken, newToken)

    const { silent, skipAuthRedirect } = res.config || {}
    if (res.data.code === 401) {
      const cleared = clearSession(res.config.sessionToken || '')
      if (cleared && !skipAuthRedirect && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      const error = new Error('登录已过期')
      error.sessionInvalidated = cleared
      error.code = 401
      error.response = res
      return Promise.reject(error)
    }
    if (res.data.code !== 200 && res.data.code !== undefined) {
      if (!silent) ElMessage.error(res.data.message || '请求失败')
      return Promise.reject(new Error(res.data.message))
    }
    return res.data
  },
  err => {
    // HTTP 401（含后端业务码 401 映射）：会话失效，清登录态
    // 默认跳登录页；skipAuthRedirect 用于启动时的静默会话探测
    const { silent, skipAuthRedirect } = err.config || {}
    if (err.response?.status === 401) {
      const cleared = clearSession(err.config?.sessionToken || '')
      err.sessionInvalidated = cleared
      if (cleared && !skipAuthRedirect && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(err)
    }
    if (!silent) {
      if (err.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请稍后重试')
      } else if (err.response) {
        const msg = err.response.data?.message || '服务器错误'
        ElMessage.error(msg)
      } else {
        ElMessage.error('网络异常，请检查连接')
      }
    }
    return Promise.reject(err)
  }
)

export default request
