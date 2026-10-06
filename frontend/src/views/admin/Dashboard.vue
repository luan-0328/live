<template>
  <div>
    <div v-if="loading" class="admin-card">
      <el-skeleton :rows="4" animated />
    </div>
    <template v-else>
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ stats.userCount || 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">帖子总数</div>
          <div class="stat-value">{{ stats.postCount || 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">今日发帖</div>
          <div class="stat-value">{{ stats.todayPosts || 0 }}</div>
        </div>
        <div class="stat-card pending" @click="$router.push('/admin/reports')">
          <div class="stat-label">待处理举报</div>
          <div class="stat-value">{{ stats.pendingReports || 0 }}</div>
          <div class="stat-hint">点击前往处理 →</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '../../api/admin'

const stats = ref({})
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDashboard()
    stats.value = res.data || res || {}
  } catch {
    stats.value = {}
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px;
  transition: transform 0.2s;
}
.stat-card.pending {
  cursor: pointer;
}
.stat-card.pending:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
}
.stat-label {
  font-size: 13px;
  color: var(--text-3);
  margin-bottom: 10px;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--brand);
}
.stat-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-3);
}
.admin-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px;
}
</style>
