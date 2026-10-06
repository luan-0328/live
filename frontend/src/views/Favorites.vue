<template>
  <div class="fav-page">
    <div class="page-header">
      <h2 class="page-title">我的收藏</h2>
    </div>
    <div v-if="loading" class="fav-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="posts.length === 0" class="fav-empty">
      <el-empty description="还没有收藏过帖子" />
    </div>
    <template v-else>
      <div class="fav-list">
        <PostCard v-for="post in posts" :key="post.postId" :post="post" />
      </div>
      <div v-if="total > 20" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="prev, pager, next"
          background
          small
          @current-change="loadFavs"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { latestRequest } from '../utils/latestRequest'
import { ref, onMounted, onUnmounted } from 'vue'
import { getFavorites } from '../api/user'
import PostCard from '../components/PostCard.vue'

const posts = ref([])
const total = ref(0)
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const page = ref(1)

onMounted(() => loadFavs())

async function loadFavs() {
  const id = requests.start()
  loading.value = true
  try {
    const res = await getFavorites({ page: page.value, size: 20 })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    posts.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { posts.value = []; total.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loading.value = false
  }
}
</script>

<style scoped>
.fav-page {
  max-width: 800px;
  margin: 0 auto;
}
.page-header {
  margin: 4px 0 18px;
}
.page-title {
  font-size: 21px;
  font-weight: 700;
  color: var(--text);
}
.fav-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.fav-loading {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.fav-empty {
  padding: 60px 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
</style>
