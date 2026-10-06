<template>
  <div class="admin-card">
    <h3 class="card-title">操作日志</h3>
    <div v-if="loading" class="card-loading">
      <el-skeleton :rows="6" animated />
    </div>
    <template v-else>
      <el-table :data="logs" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="adminNickname" label="操作人" width="120">
          <template #default="{ row }">
            {{ row.adminNickname || `#${row.adminId || ''}` }}
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="140" />
        <el-table-column prop="targetType" label="对象类型" width="100" />
        <el-table-column prop="targetId" label="对象ID" width="90" />
        <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          small
          @current-change="loadLogs"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { latestRequest } from '../../utils/latestRequest'
import { ref, onMounted, onUnmounted } from 'vue'
import { listAdminLogs } from '../../api/admin'

const logs = ref([])
const total = ref(0)
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const page = ref(1)
const pageSize = 20

onMounted(() => loadLogs())

async function loadLogs() {
  const id = requests.start()
  loading.value = true
  try {
    const res = await listAdminLogs({ page: page.value, size: pageSize })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    logs.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { logs.value = []; total.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loading.value = false
  }
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
