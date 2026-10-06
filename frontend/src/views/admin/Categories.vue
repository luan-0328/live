<template>
  <div class="admin-card">
    <div class="card-header">
      <h3 class="card-title">分类管理</h3>
      <el-button type="primary" size="small" @click="showDialog(null)">新增分类</el-button>
    </div>
    <div v-if="loading" class="card-loading">
      <el-skeleton :rows="4" animated />
    </div>
    <template v-else>
      <el-table :data="categories" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新增分类'" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="分类名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { listCategories, createCategory, updateCategory, deleteCategory } from '../../api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'

const categories = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = reactive({ name: '', sort: 0 })

onMounted(() => loadCategories())

async function loadCategories() {
  loading.value = true
  try {
    const res = await listCategories()
    categories.value = res.data || res || []
  } catch {
    categories.value = []
  } finally {
    loading.value = false
  }
}

function showDialog(row) {
  if (row) {
    editing.value = row.id
    form.name = row.name
    form.sort = row.sort
  } else {
    editing.value = null
    form.name = ''
    form.sort = 0
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) return ElMessage.warning('请输入名称')
  saving.value = true
  try {
    if (editing.value != null) {
      await updateCategory(editing.value, { name: form.name, sort: form.sort })
    } else {
      await createCategory({ name: form.name, sort: form.sort })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadCategories()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除分类"${row.name}"？`)
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    loadCategories()
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
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin: 0;
}
.card-loading {
  padding: 24px;
}
</style>
