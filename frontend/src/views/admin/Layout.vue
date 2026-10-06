<template>
  <div class="admin-layout">
    <div class="admin-sidebar">
      <div class="admin-logo">管理后台</div>
      <el-menu
        :default-active="activeMenu"
        router
        :ellipsis="false"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/categories">
          <el-icon><Collection /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/reports">
          <el-icon><WarningFilled /></el-icon>
          <span>举报处理</span>
        </el-menu-item>
        <el-menu-item index="/admin/posts">
          <el-icon><Document /></el-icon>
          <span>帖子管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/logs">
          <el-icon><List /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
      </el-menu>
    </div>
    <div class="admin-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { User, Collection, WarningFilled, DataLine, Document, List } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.admin-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.admin-sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.admin-logo {
  padding: 16px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  border-bottom: 1px solid var(--border);
  background: linear-gradient(165deg, #f8f9ff, var(--surface));
}
.admin-main {
  flex: 1;
  min-width: 0;
}
.el-menu {
  border-right: none;
}
</style>
