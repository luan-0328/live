<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link to="/" class="logo">地方社区</router-link>

      <div class="search-box">
        <el-input
          v-model="keyword"
          placeholder="搜索帖子、话题..."
          :prefix-icon="Search"
          clearable
          maxlength="100"
          @keyup.enter="doSearch"
        />
      </div>

      <nav class="header-nav">
        <template v-if="userStore.isLogin">
          <router-link to="/notifications" class="nav-item">
            <el-badge :value="userStore.unreadCount" :max="99" :hidden="userStore.unreadCount === 0">
              <el-icon><Bell /></el-icon>
            </el-badge>
            <span>通知</span>
          </router-link>
          <router-link to="/favorites" class="nav-item">
            <el-icon><Star /></el-icon>
            <span>收藏</span>
          </router-link>
          <router-link to="/create" class="nav-item post-btn">
            <el-button type="primary" size="small">发帖</el-button>
          </router-link>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="avatarUrl" />
              <span class="nickname">{{ userStore.user?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="center">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin">管理后台</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="nav-item login-btn">登录</router-link>
          <router-link to="/register" class="nav-item register-btn">注册</router-link>
        </template>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { logout as logoutApi } from '../api/auth'
import { Search, Bell, Star, ArrowDown } from '@element-plus/icons-vue'
import { DEFAULT_AVATAR } from '../constants'

const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')

const avatarUrl = computed(() => {
  return userStore.user?.avatar || DEFAULT_AVATAR
})

let unreadTimer
onMounted(() => {
  if (userStore.isLogin) userStore.refreshUnreadCount()
  unreadTimer = setInterval(() => {
    if (userStore.isLogin && document.visibilityState === 'visible') userStore.refreshUnreadCount()
  }, 30000)
})
onUnmounted(() => clearInterval(unreadTimer))

watch(() => userStore.isLogin, (v) => {
  if (v) userStore.refreshUnreadCount()
  else userStore.unreadCount = 0
})

function doSearch() {
  if (keyword.value.trim()) {
    router.push({ path: '/search', query: { keyword: keyword.value.trim() } })
  }
}

async function handleCommand(cmd) {
  if (cmd === 'center') router.push('/user/center')
  else if (cmd === 'admin') router.push('/admin')
  else if (cmd === 'logout') {
    try { await logoutApi() } catch { return }
    userStore.logout()
    router.push('/')
  }
}
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 1px 2px rgba(24, 39, 75, 0.03);
  z-index: 1000;
}
.header-inner {
  max-width: 1200px;
  height: 100%;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.logo {
  font-size: 22px;
  font-weight: 800;
  background: var(--brand-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: 0.5px;
  white-space: nowrap;
  flex-shrink: 0;
}
.search-box {
  flex: 1;
  max-width: 480px;
}
.header-nav {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-2);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
}
.nav-item:hover {
  color: var(--brand);
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-info:hover {
  background: var(--surface-2);
}
.nickname {
  font-size: 14px;
  color: var(--text);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.login-btn {
  padding: 7px 18px;
  background: var(--brand-grad);
  color: #fff !important;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(45, 92, 255, 0.28);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(45, 92, 255, 0.34);
}
.register-btn {
  padding: 7px 18px;
  border: 1px solid var(--brand);
  color: var(--brand) !important;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s;
}
.register-btn:hover {
  background: var(--brand-light);
}
.post-btn {
  margin-right: 2px;
}
</style>
