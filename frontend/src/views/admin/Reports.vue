<template>
  <div class="admin-card">
    <h3 class="card-title">举报处理</h3>
    <div v-if="loading" class="card-loading">
      <el-skeleton :rows="6" animated />
    </div>
    <template v-else>
      <el-table :data="reports" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="targetType" label="类型" width="80" />
        <el-table-column prop="targetId" label="目标ID" width="70" />
        <el-table-column prop="reason" label="举报原因" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'warning' : (row.status === 1 ? 'success' : 'info')" size="small">
              {{ row.status === 0 ? '待处理' : (row.status === 1 ? '已处理' : '已驳回') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="primary"
              @click="handleReportAction(row, 1)"
            >通过</el-button>
            <el-button
              v-if="row.status === 0"
              size="small"
              @click="handleReportAction(row, 2)"
            >驳回</el-button>
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
          @current-change="loadReports"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { latestRequest } from '../../utils/latestRequest'
import { ref, onMounted, onUnmounted } from 'vue'
import { listReports, handleReport } from '../../api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'

const reports = ref([])
const total = ref(0)
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const page = ref(1)
const pageSize = 20

onMounted(() => loadReports())

async function loadReports() {
  const id = requests.start()
  loading.value = true
  try {
    const res = await listReports({ page: page.value, size: pageSize })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    reports.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { reports.value = []; total.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loading.value = false
  }
}

async function handleReportAction(row, status) {
  const label = status === 1 ? '通过' : '驳回'
  try {
    const { value: note } = await ElMessageBox.prompt(`请输入${label}备注`, '处理举报', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '备注（选填，最多500字）',
      inputValidator: value => !value || value.length <= 500 || '备注最多500字'
    })
    await handleReport(row.id, { status, handleNote: note || `${label}处理` })
    ElMessage.success(`已${label}`)
    loadReports()
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
