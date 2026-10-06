<template>
  <div class="login-page">
    <div class="login-card">
      <div class="card-brand">
        <div class="brand-badge">地</div>
        <div class="brand-text">
          <div class="brand-name">地方社区</div>
          <div class="brand-sub">本地生活信息分享平台</div>
        </div>
      </div>

      <div class="login-tabs">
        <span :class="{ active: tab === 'user' }" @click="tab = 'user'">用户登录</span>
        <span :class="{ active: tab === 'admin' }" @click="tab = 'admin'">管理员登录</span>
      </div>

      <!-- 用户登录 -->
      <template v-if="tab === 'user'">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
          <el-form-item prop="phone">
            <el-input
              v-model="form.phone"
              placeholder="手机号"
              :prefix-icon="Phone"
              size="large"
              maxlength="11"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              placeholder="密码"
              :prefix-icon="Lock"
              size="large"
              type="password"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="handleLogin" class="submit-btn">
              登录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="card-footer">
          <router-link to="/register">没有账号？立即注册</router-link>
        </div>
      </template>

      <!-- 管理员登录 -->
      <template v-if="tab === 'admin'">
        <el-form ref="adminFormRef" :model="adminForm" :rules="adminRules" label-width="0">
          <el-form-item prop="account">
            <el-input
              v-model="adminForm.account"
              placeholder="管理员账号"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="adminForm.password"
              placeholder="密码"
              :prefix-icon="Lock"
              size="large"
              type="password"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" size="large" :loading="loading" @click="handleAdminLogin" class="submit-btn">
              管理员登录
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login, adminLogin } from '../api/auth'
import { Phone, User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const tab = ref('user')
const formRef = ref(null)
const adminFormRef = ref(null)
const loading = ref(false)

const form = reactive({
  phone: '',
  password: ''
})

const adminForm = reactive({
  account: '',
  password: ''
})

const adminRules = {
  account: [{ required: true, message: '请输入管理员账号' }],
  password: [{ required: true, message: '请输入密码' }]
}

const rules = {
  phone: [
    { required: true, message: '请输入手机号' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确' }
  ],
  password: [
    { required: true, message: '请输入密码' }
  ]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await login(form.phone, form.password)
    const data = res.data || res
    userStore.setToken(data.token)
    userStore.setUser({ userId: data.userId, nickname: data.nickname, role: data.role })
    try {
      await userStore.fetchUserInfo()
    } catch {}
    const target = route.query.redirect
    const redirect = typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') ? target : '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

async function handleAdminLogin() {
  const valid = await adminFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await adminLogin(adminForm.account, adminForm.password)
    const data = res.data || res
    userStore.setToken(data.token)
    userStore.setUser({ userId: data.userId, nickname: data.nickname, role: data.role })
    try {
      await userStore.fetchUserInfo()
    } catch {}
    ElMessage.success('欢迎回来，管理员')
    router.push('/admin')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-page {
  position: relative;
  min-height: calc(100vh - 120px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 44px 16px 40px;
}
.login-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(560px 400px at 10% 14%, rgba(45, 92, 255, 0.10), transparent 62%),
    radial-gradient(480px 360px at 92% 22%, rgba(122, 92, 255, 0.10), transparent 62%);
}
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  padding: 34px 40px 32px;
}
.card-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}
.brand-badge {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 14px rgba(45, 92, 255, 0.35);
}
.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: 1px;
}
.brand-sub {
  font-size: 13px;
  color: var(--text-3);
  margin-top: 2px;
}
.card-title {
  text-align: center;
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin-bottom: 28px;
}
.code-row {
  display: flex;
  gap: 10px;
}
.code-btn {
  width: 130px;
  flex-shrink: 0;
}
.submit-btn {
  width: 100%;
}
.card-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
}
.login-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  margin-bottom: 28px;
}
.login-tabs span {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  font-size: 15px;
  color: var(--text-3);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.login-tabs span.active {
  color: var(--brand);
  font-weight: 600;
}
.login-tabs span.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 3px;
  background: var(--brand-grad);
  border-radius: 2px;
}
.login-tabs span:hover {
  color: var(--brand);
}
</style>
