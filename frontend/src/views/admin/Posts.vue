<template>
  <div class="admin-card">
    <h3 class="card-title">帖子管理</h3>
    <div v-if="loading" class="card-loading">
      <el-skeleton :rows="6" animated />
    </div>
    <template v-else>
      <el-table :data="posts" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/post/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="authorId" label="作者ID" width="80" />
        <el-table-column prop="categoryId" label="分类ID" width="80" />
        <el-table-column prop="likeCount" label="点赞" width="60" />
        <el-table-column prop="commentCount" label="评论" width="60" />
        <el-table-column prop="viewCount" label="浏览" width="60" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已删除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发布时间" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="danger" @click="handleForceDelete(row)">
              强制删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          small
          @current-change="loadPosts"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { latestRequest } from '../../utils/latestRequest'
import { ref, onMounted, onUnmounted } from 'vue'
import { listAdminPosts, forceDeletePost } from '../../api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'

const posts = ref([])
const total = ref(0)
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const page = ref(1)
const pageSize = 20

onMounted(() => loadPosts())

async function loadPosts() {
  const id = requests.start()
  loading.value = true
  try {
    const res = await listAdminPosts({ page: page.value, size: pageSize })
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

async function handleForceDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定强制删除帖子「${row.title}」？其点赞、收藏、评论将被一并清理。`,
      '强制删除',
      { type: 'warning' }
    )
    await forceDeletePost(row.id)
    ElMessage.success('已删除')
    loadPosts()
  } catch {}
}
</script>

<style scoped>
.admin-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16px;
}
.card-loading {
  padding: 24px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
