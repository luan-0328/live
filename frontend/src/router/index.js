import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue') },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue') },
  { path: '/post/:id', name: 'PostDetail', component: () => import('../views/PostDetail.vue') },
  { path: '/post/:id/edit', name: 'EditPost', component: () => import('../views/CreatePost.vue'), meta: { requiresAuth: true } },
  { path: '/create', name: 'CreatePost', component: () => import('../views/CreatePost.vue'), meta: { requiresAuth: true } },
  { path: '/user/:id', name: 'UserProfile', component: () => import('../views/UserProfile.vue') },
  { path: '/user/center', name: 'UserCenter', component: () => import('../views/UserCenter.vue'), meta: { requiresAuth: true } },
  { path: '/search', name: 'Search', component: () => import('../views/Search.vue') },
  { path: '/notifications', name: 'Notifications', component: () => import('../views/Notifications.vue'), meta: { requiresAuth: true } },
  { path: '/favorites', name: 'Favorites', component: () => import('../views/Favorites.vue'), meta: { requiresAuth: true } },
  {
    path: '/admin',
    component: () => import('../views/admin/Layout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('../views/admin/Users.vue') },
      { path: 'categories', name: 'AdminCategories', component: () => import('../views/admin/Categories.vue') },
      { path: 'reports', name: 'AdminReports', component: () => import('../views/admin/Reports.vue') },
      { path: 'posts', name: 'AdminPosts', component: () => import('../views/admin/Posts.vue') },
      { path: 'logs', name: 'AdminLogs', component: () => import('../views/admin/Logs.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userStr = localStorage.getItem('user')

  // 后台路由需要管理员权限
  if (to.path.startsWith('/admin')) {
    if (!token) return next({ path: '/login', query: { redirect: to.fullPath } })
    try {
      const user = JSON.parse(userStr)
      if (user?.role !== 'ROLE_ADMIN') return next('/')
    } catch {
      return next('/login')
    }
  }

  if (to.meta.requiresAuth && !token) return next({ path: '/login', query: { redirect: to.fullPath } })
  next()
})

export default router
