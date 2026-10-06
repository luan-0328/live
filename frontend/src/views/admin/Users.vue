<template>
  <div class="admin-card">
    <h3 class="card-title">用户管理</h3>
    <div v-if="loading" class="card-loading">
      <el-skeleton :rows="6" animated />
    </div>
    <template v-else>
      <el-table :data="users" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ROLE_ADMIN' ? 'danger' : 'info'" size="small">
              {{ row.role === 'ROLE_ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : (row.status === -1 ? '注销' : '封禁') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="postCount" label="帖子" width="60" />
        <el-table-column prop="followerCount" label="粉丝" width="60" />
        <el-table-column prop="createdAt" label="注册时间" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.status === -1 || row.id === userStore.user?.userId || busyUser === row.id"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleBan(row)"
            >
              {{ row.status === -1 ? '已注销' : (row.status === 1 ? '封禁' : '解封') }}
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
          @current-change="loadUsers"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { latestRequest } from '../../utils/latestRequest'
import { useUserStore } from '../../stores/user'
import { ref, onMounted, onUnmounted } from 'vue'
import { listUsers, banUser } from '../../api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'

const userStore = useUserStore()
const busyUser = ref(null)
const users = ref([])
const total = ref(0)
const loading = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)
const page = ref(1)
const pageSize = 20

onMounted(() => loadUsers())

async function loadUsers() {
  const id = requests.start()
  loading.value = true
  try {
    const res = await listUsers({ page: page.value, size: pageSize })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    users.value = data.records || []
    total.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { users.value = []; total.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loading.value = false
  }
}

async function toggleBan(row) {
  if (busyUser.value != null || row.status === -1) return
  busyUser.value = row.id
  const action = row.status === 1 ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确定${action}用户"${row.nickname}"？`)
    await banUser(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(`${action}成功`)
    loadUsers()
  } catch {} finally { busyUser.value = null }
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
