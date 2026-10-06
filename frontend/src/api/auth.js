import request from './request'

export const sendCode = (phone) =>
  request.post('/auth/send-code', { phone })

export const register = (phone, code, nickname, password) =>
  request.post('/auth/register', { phone, code, nickname, password })

export const login = (phone, password) =>
  request.post('/auth/login', { phone, password })

export const adminLogin = (account, password) =>
  request.post('/auth/admin-login', { account, password })

export const logout = () =>
  request.post('/auth/logout')

// 会话校验：供启动时探测登录态是否仍有效
// skipAuthRedirect 避免探测失败时被强制跳转登录页，silent 避免弹出错误提示
export const checkSession = () =>
  request.get('/auth/check', { skipAuthRedirect: true, silent: true, timeout: 5000 })
