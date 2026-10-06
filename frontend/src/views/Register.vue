<template>
  <div class="register-page">
    <div class="register-card">
      <div class="card-brand">
        <div class="brand-badge">地</div>
        <div class="brand-text">
          <div class="brand-name">加入地方社区</div>
          <div class="brand-sub">创建账号，分享身边新鲜事</div>
        </div>
      </div>
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
        <el-form-item prop="code">
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="验证码"
              :prefix-icon="Key"
              size="large"
              maxlength="6"
            />
            <el-button
              :disabled="sending || countdown > 0"
              size="large"
              class="code-btn"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : (sending ? '发送中' : '获取验证码') }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="昵称（2-12个字符）"
            :prefix-icon="User"
            size="large"
            maxlength="12"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            placeholder="密码（8~64位，含字母和数字）"
            :prefix-icon="Lock"
            size="large"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleRegister" class="submit-btn">
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="card-footer">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { register, sendCode as sendSms } from '../api/auth'
import { Phone, Key, User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({
  phone: '',
  code: '',
  nickname: '',
  password: ''
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确' }
  ],
  code: [
    { required: true, message: '请输入验证码' },
    { pattern: /^\d{6}$/, message: '验证码须为6位数字' }
  ],
  nickname: [
    { required: true, message: '请输入昵称' },
    { min: 2, max: 12, message: '昵称长度2-12个字符' }
  ],
  password: [
    { required: true, message: '请设置密码' },
    { min: 8, max: 64, message: '密码长度需为 8~64 位' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]+$/, message: '密码需至少含一个字母和一个数字，且只能用字母、数字和常见符号' }
  ]
}

async function sendCode() {
  const valid = await formRef.value.validateField('phone').catch(() => false)
  if (!valid) return
  sending.value = true
  try {
    await sendSms(form.phone)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally {
    sending.value = false
  }
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await register(form.phone, form.code, form.nickname, form.password)
    const data = res.data || res
    userStore.setToken(data.token)
    userStore.setUser({ userId: data.userId, nickname: data.nickname, role: data.role })
    try {
      await userStore.fetchUserInfo()
    } catch {}
    ElMessage.success('注册成功')
    router.push('/')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.register-page {
  position: relative;
  min-height: calc(100vh - 120px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 44px 16px 40px;
}
.register-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(560px 400px at 10% 14%, rgba(45, 92, 255, 0.10), transparent 62%),
    radial-gradient(480px 360px at 92% 22%, rgba(122, 92, 255, 0.10), transparent 62%);
}
.register-card {
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
</style>
