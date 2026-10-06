import { defineStore } from 'pinia'
import { ref, computed, onScopeDispose } from 'vue'
import { readSession, saveToken, saveUser, clearSession, subscribeSession } from '../utils/session'
import { latestRequest } from '../utils/latestRequest'
import { getUserInfo } from '../api/user'
import { getUnreadCount } from '../api/notification'
import { checkSession as checkSessionApi } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref(parseUser())
  function parseUser() {
    try { return JSON.parse(localStorage.getItem('user') || 'null') } catch { return null }
  }
  const token = ref(localStorage.getItem('token') || '')
  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  const unreadCount = ref(0)
  const profileRequests = latestRequest()
  const unreadRequests = latestRequest()
  onScopeDispose(subscribeSession(() => {
    const session = readSession()
    token.value = session.token
    user.value = session.user
    if (!session.token) unreadCount.value = 0
  }))

  function setUser(u) {
    const value = u ? { ...u, userId: u.userId ?? u.id } : null
    saveUser(value)
  }

  function setToken(t) {
    saveToken(t)
  }

  async function fetchUserInfo() {
    const id = profileRequests.start()
    const sessionUserId = user.value?.userId
    try {
      const res = await getUserInfo()
      const data = res.data || res
      // 后端 User 实体返回 id，前端统一用 userId
      if (data && data.id != null && data.userId == null) {
        data.userId = data.id
      }
      if (profileRequests.isCurrent(id) && isLogin.value && user.value?.userId === sessionUserId) setUser(data)
    } catch (e) {
      // 只有 401 等鉴权失败才登出，临时网络错误不登出
      if (e?.sessionInvalidated) {
        logout()
      }
    }
  }

  function logout() {
    profileRequests.cancel()
    unreadRequests.cancel()
    clearSession()
    unreadCount.value = 0
  }

  // 启动时校验本地 token 的会话是否仍有效；失效则自动登出
  // 返回 true 表示当前仍为有效登录态
  async function checkSession() {
    if (!token.value) return false
    try {
      await checkSessionApi()
      return true
    } catch (e) {
      // 仅鉴权失败（401）才登出；网络异常/超时保留登录态，避免误登出
      if (e?.sessionInvalidated) {
        logout()
        return false
      }
      return isLogin.value
    }
  }

  async function refreshUnreadCount() {
    const id = unreadRequests.start()
    const sessionUserId = user.value?.userId
    try {
      const res = await getUnreadCount()
      const data = res.data || res
      if (!unreadRequests.isCurrent(id) || !isLogin.value || user.value?.userId !== sessionUserId) return
      unreadCount.value = typeof data === 'number' ? data : 0
    } catch {
      if (unreadRequests.isCurrent(id) && user.value?.userId === sessionUserId) unreadCount.value = 0
    }
  }

  return { user, token, isLogin, isAdmin, unreadCount, setUser, setToken, fetchUserInfo, logout, checkSession, refreshUnreadCount }
})
