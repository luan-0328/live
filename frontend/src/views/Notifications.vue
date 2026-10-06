<template>
  <div class="notif-page">
    <div class="notif-card">
      <div class="card-header">
        <h2 class="page-title">消息通知</h2>
        <el-button v-if="list.length > 0" size="small" @click="markAllRead">全部已读</el-button>
      </div>
      <div v-if="loading" class="notif-loading">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="list.length === 0" class="notif-empty">
        <el-empty description="暂无通知" />
      </div>
      <template v-else>
        <div
          v-for="item in list"
          :key="item.id"
          class="notif-item"
          :class="{ unread: !item.isRead }"
          @click="handleClick(item)"
        >
          <div class="notif-dot" v-if="!item.isRead" />
          <div class="notif-content">
            <p class="notif-text">{{ item.fromUser?.nickname || '已注销用户' }} {{ item.content }}</p>
            <span class="notif-time">{{ item.createdAt }}</span>
          </div>
        </div>
        <div v-if="total > pageSize" class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            background
            small
            @current-change="loadList"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { latestRequest } from '../utils/latestRequest'
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getNotifications, markRead, markAllRead as markAllReadApi } from '../api/notification'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const total = ref(0)
const currentPage = ref(1)
const pageSize = 20

onMounted(() => loadList())

async function loadList(page) {
  const id = requests.start()
  loading.value = true
  currentPage.value = page || 1
  try {
    const res = await getNotifications({ page: currentPage.value, size: pageSize })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    list.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { list.value = []; total.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loading.value = false
  }
}

async function markAllRead() {
  try {
    await markAllReadApi()
    list.value.forEach(item => { item.isRead = true })
    await userStore.refreshUnreadCount()
    ElMessage.success('已全部标记已读')
  } catch {}
}

async function handleClick(item) {
  if (!item.isRead) {
    try {
      await markRead(item.id)
      item.isRead = true
      await userStore.refreshUnreadCount()
    } catch {}
  }
  if (item.postId) router.push(`/post/${item.postId}`)
  else if (item.type === 'follow' && item.fromUserId) router.push(`/user/${item.fromUserId}`)
}
</script>

<style scoped>
.notif-page {
  max-width: 700px;
  margin: 0 auto;
}
.notif-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(165deg, #f8f9ff, var(--surface));
}
.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  margin: 0;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 16px;
}
.notif-loading {
  padding: 24px;
}
.notif-empty {
  padding: 60px 0;
}
.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s;
}
.notif-item:last-child {
  border-bottom: none;
}
.notif-item:hover {
  background: var(--surface-2);
}
.notif-item.unread {
  background: #f2f6ff;
}
.notif-dot {
  width: 8px;
  height: 8px;
  background: var(--brand);
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 6px;
  box-shadow: 0 0 0 3px rgba(45, 92, 255, 0.15);
}
.notif-content {
  flex: 1;
  min-width: 0;
}
.notif-text {
  font-size: 14px;
  color: var(--text);
  line-height: 1.5;
  margin-bottom: 4px;
}
.notif-time {
  font-size: 12px;
  color: var(--text-3);
}
</style>
