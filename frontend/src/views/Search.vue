<template>
  <div class="search-page">
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索帖子、话题..."
        size="large"
        :prefix-icon="SearchIcon"
        clearable
        maxlength="100"
        @keyup.enter="doSearch"
        style="flex:1"
      />
      <el-select v-model="categoryId" placeholder="全部分类" clearable size="large" style="width:140px">
        <el-option
          v-for="cat in categories"
          :key="cat.id"
          :label="cat.name"
          :value="cat.id"
        />
      </el-select>
      <el-button type="primary" size="large" @click="doSearch">搜索</el-button>
    </div>

    <div class="search-meta" v-if="keyword">
      搜索"{{ keyword }}"的结果，共找到 {{ total }} 条
    </div>

    <div class="search-results">
      <div v-if="loading" class="results-loading">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="posts.length === 0 && keyword" class="results-empty">
        <el-empty description="没有找到相关内容" />
      </div>
      <template v-else>
        <PostCard v-for="post in posts" :key="post.postId" :post="post" />
        <div v-if="total > 20" class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            :page-size="20"
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
</template>

<script setup>

import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchPosts } from '../api/post'
import { getCategories } from '../api/category'
import { latestRequest } from '../utils/latestRequest'
import PostCard from '../components/PostCard.vue'
import { Search as SearchIcon } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const categoryId = ref(undefined)
const categories = ref([])
const posts = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const requests = latestRequest()
onUnmounted(requests.cancel)
onMounted(async () => {
  try { categories.value = (await getCategories()).data || [] } catch {}
})

watch(() => route.query, query => {
  keyword.value = String(query.keyword || '').slice(0, 100)
  categoryId.value = Number(query.categoryId) > 0 ? Number(query.categoryId) : undefined
  page.value = Math.max(1, Number.parseInt(query.page) || 1)
  loadResults()
}, { immediate: true })

async function doSearch() {
  const query = { keyword: keyword.value.trim(), page: 1 }
  if (categoryId.value) query.categoryId = categoryId.value
  if (String(route.query.keyword || '') === query.keyword && Number(route.query.page || 1) === 1
      && Number(route.query.categoryId || 0) === Number(categoryId.value || 0)) {
    page.value = 1
    return loadResults()
  }
  await router.push({ query })
}

function changePage(value) { router.push({ query: { ...route.query, page: value } }) }

async function loadResults() {
  const id = requests.start()
  if (!keyword.value.trim()) { posts.value = []; total.value = 0; loading.value = false; return }
  loading.value = true
  try {
    const res = await searchPosts({ keyword: keyword.value.trim(), categoryId: categoryId.value, page: page.value, size: 20 })
    if (!requests.isCurrent(id)) return
    posts.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    if (requests.isCurrent(id)) { posts.value = []; total.value = 0 }
  } finally { if (requests.isCurrent(id)) loading.value = false }
}

</script>

<style scoped>
.search-page {
  max-width: 800px;
  margin: 0 auto;
}
.search-bar {
  display: flex;
  gap: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 16px;
  margin-bottom: 16px;
}
.search-meta {
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 14px;
  padding-left: 2px;
}
.search-results {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.results-loading {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.results-empty {
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
