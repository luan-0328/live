import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import {
  Search, Bell, Star, ArrowDown, Phone, User, Lock,
  Goods, Delete, View, ChatLineRound, Location,
  Document, Edit, Iphone, Setting, Collection, WarningFilled,
  Key, Plus
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import './styles/global.css'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  // 全局 Vue 渲染错误兜底，防止白屏
  app.config.errorHandler = (err, _vm, info) => {
    console.error('Vue 渲染错误:', err, info)
  }

  // 挂载前校验本地 token 会话是否仍有效（JWT 未过期且 Redis 会话存活）。
  // 长时间未登录的旧账号会在此自动登出，顶栏直接显示未登录状态，
  // 而不是"看着像已登录，点需要登录的接口才被踢回登录页"。
  const userStore = useUserStore()
  if (userStore.token) {
    if (await userStore.checkSession()) await userStore.fetchUserInfo()
  }

  app.use(router)
  app.use(ElementPlus)

  // 仅注册实际使用的图标，避免全局导入全部 300+ 图标导致包体积膨胀
  const icons = [
    Search, Bell, Star, ArrowDown, Phone, User, Lock,
    Goods, Delete, View, ChatLineRound, Location,
    Document, Edit, Iphone, Setting, Collection, WarningFilled,
    Key, Plus
  ]
  icons.forEach(icon => app.component(icon.name, icon))

  app.mount('#app')
}

bootstrap()
