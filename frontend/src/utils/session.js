// HTTP层和Pinia共享登录态，避免续期、退出和过期响应只更新其中一处。
const SESSION_EVENT = 'community-session-change'

export function readSession() {
  let user = null
  try { user = JSON.parse(localStorage.getItem('user') || 'null') } catch {}
  return { token: localStorage.getItem('token') || '', user }
}

function notify() { window.dispatchEvent(new Event(SESSION_EVENT)) }

export function saveToken(token) {
  if (token) localStorage.setItem('token', token)
  else localStorage.removeItem('token')
  notify()
}

export function saveUser(user) {
  if (user) localStorage.setItem('user', JSON.stringify(user))
  else localStorage.removeItem('user')
  notify()
}

export function clearSession(expectedToken) {
  if (expectedToken !== undefined && readSession().token !== expectedToken) return false
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  notify()
  return true
}

export function rotateToken(expectedToken, newToken) {
  if (!expectedToken || readSession().token !== expectedToken) return false
  saveToken(newToken)
  return true
}

export function subscribeSession(callback) {
  window.addEventListener(SESSION_EVENT, callback)
  window.addEventListener('storage', callback)
  return () => {
    window.removeEventListener(SESSION_EVENT, callback)
    window.removeEventListener('storage', callback)
  }
}
