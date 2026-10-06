<template>
  <div class="home-page">
    <div class="home-main">
      <!-- 分类标签 -->
      <div class="category-bar">
        <el-tabs v-model="currentCategory" @tab-change="changeFilters">
          <el-tab-pane label="全部" :name="''" />
          <el-tab-pane
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.name"
            :name="String(cat.id)"
          />
        </el-tabs>
      </div>

      <!-- 排序 + 发帖按钮 -->
      <div class="sort-bar">
        <div class="sort-tabs">
          <span
            v-for="tab in sortTabs"
            :key="tab.key"
            class="sort-item"
            :class="{ active: currentSort === tab.key }"
            @click="currentSort = tab.key; changeFilters()"
          >{{ tab.label }}</span>
        </div>
        <div>
          <el-button size="small" :loading="locating" @click="showNearby">附近帖子</el-button>
          <el-select v-if="currentSort === 'nearby'" v-model="radius" style="width:100px" @change="changeFilters">
            <el-option v-for="km in [1, 5, 10, 20, 50]" :key="km" :label="`${km}公里`" :value="km" />
          </el-select>
        </div>
        <router-link v-if="userStore.isLogin" to="/create" class="create-link">
          <el-button type="primary" size="small">发布帖子</el-button>
        </router-link>
      </div>

      <!-- 帖子列表 -->
      <div class="post-list">
        <div v-if="loading" class="list-loading">
          <el-skeleton :rows="5" animated />
        </div>
        <div v-else-if="posts.length === 0" class="list-empty">
          <el-empty :description="currentSort === 'nearby' && !position ? '请点击附近帖子获取位置' : '暂无帖子'" />
        </div>
        <template v-else>
          <PostCard v-for="post in posts" :key="post.postId" :post="post" />
          <div v-if="total > pageSize" class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              background
              small
              @current-change="changePage"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 右侧栏 -->
    <aside class="home-sidebar">
      <div v-if="!userStore.isLogin" class="login-prompt">
        <div class="prompt-icon"><el-icon><User /></el-icon></div>
        <p>登录后即可发帖交流</p>
        <router-link to="/login">
          <el-button type="primary" round size="small">立即登录</el-button>
        </router-link>
      </div>
      <HotRank />
    </aside>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { latestRequest } from '../utils/latestRequest'
import { getPostList, getNearbyPosts } from '../api/post'
import { ElMessage } from 'element-plus'
import { getCategories } from '../api/category'
import PostCard from '../components/PostCard.vue'
import HotRank from '../components/HotRank.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sortTabs = [
  { key: 'latest', label: '最新' },
  { key: 'hot', label: '点赞最多' }
]

const categories = ref([])
const currentCategory = ref('')
const currentSort = ref('latest')
const posts = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20
const cancelled = ref(false)
const requests = latestRequest()
const locating = ref(false)
const position = ref(null)
const radius = ref(10)
onUnmounted(() => { cancelled.value = true; requests.cancel() })

onMounted(async () => {
  await loadCategories()
})

// 路由回退时保留分类参数
watch(() => route.query, (q) => {
  if (q.categoryId) {
    currentCategory.value = String(q.categoryId)
  } else currentCategory.value = ''
  currentSort.value = ['latest', 'hot', 'nearby'].includes(q.sort) ? q.sort : 'latest'
  radius.value = [1, 5, 10, 20, 50].includes(Number(q.radius)) ? Number(q.radius) : 10
  loadPosts(Math.max(1, Number.parseInt(q.page) || 1))
}, { immediate: true })

async function loadCategories() {
  try {
    const res = await getCategories()
    categories.value = res.data || res || []
  } catch {
    categories.value = []
  }
}

async function showNearby() {
  if (!navigator.geolocation) return ElMessage.warning('当前浏览器不支持定位')
  locating.value = true
  try {
    const location = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 10000 }))
    if (cancelled.value) return
    position.value = { longitude: location.coords.longitude, latitude: location.coords.latitude }
    currentSort.value = 'nearby'
    await changeFilters()
  } catch { ElMessage.warning('无法获取位置，请允许定位并通过HTTPS访问') }
  finally { locating.value = false }
}

async function changeFilters() {
  const target = { path: '/', query: { sort: currentSort.value, categoryId: currentCategory.value || undefined,
    radius: currentSort.value === 'nearby' ? radius.value : undefined, page: 1 } }
  if (router.resolve(target).fullPath === route.fullPath) return loadPosts(1)
  await router.push(target)
}
function changePage(page) { router.push({ query: { ...route.query, page } }) }

async function loadPosts(page) {
  const id = requests.start()
  if (currentSort.value === 'nearby' && !position.value) { posts.value = []; total.value = 0; loading.value = false; return }
  loading.value = true
  currentPage.value = page || 1
  try {
    const params = {
      sort: currentSort.value,
      page: currentPage.value,
      size: pageSize
    }
    if (currentCategory.value) {
      params.categoryId = parseInt(currentCategory.value)
    }
    const res = currentSort.value === 'nearby'
      ? await getNearbyPosts({ ...params, ...position.value, radius: radius.value, sort: undefined })
      : await getPostList(params)
    if (cancelled.value || !requests.isCurrent(id)) return
    const data = res.data || res
    posts.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (cancelled.value || !requests.isCurrent(id)) return
    posts.value = []
    total.value = 0
  } finally {
    if (!cancelled.value && requests.isCurrent(id)) loading.value = false
  }
}
</script>

<style scoped>
.home-page {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.home-main {
  flex: 1;
  min-width: 0;
}
.home-sidebar {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.category-bar {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 0 18px;
  margin-bottom: 6px;
}
.sort-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 2px 14px;
}
.sort-tabs {
  display: flex;
  gap: 8px;
}
.sort-item {
  padding: 6px 16px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  border-radius: 999px;
  transition: all 0.2s;
}
.sort-item:hover {
  color: var(--brand);
  background: var(--brand-light);
}
.sort-item.active {
  color: var(--brand);
  background: var(--brand-light);
  font-weight: 600;
}
.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.list-loading {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.list-empty {
  padding: 40px 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
.login-prompt {
  background: linear-gradient(165deg, #ffffff 0%, #eef2ff 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 26px 20px;
  text-align: center;
}
.prompt-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 20px;
}
.login-prompt p {
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 14px;
}
@media (max-width: 960px) {
  .home-sidebar {
    display: none;
  }
}
</style>
