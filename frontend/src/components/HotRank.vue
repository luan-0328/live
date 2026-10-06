<template>
  <div class="hot-rank">
    <div class="rank-header">
      <el-icon class="rank-header-icon"><Star /></el-icon>
      热门帖子
    </div>
    <div v-if="loading" class="rank-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="list.length === 0" class="rank-empty">暂无热帖</div>
    <div v-else class="rank-list">
      <div
        v-for="(item, index) in list"
        :key="item.postId"
        class="rank-item"
        :class="{ 'top-three': index < 3 }"
        @click="$router.push(`/post/${item.postId}`)"
      >
        <span class="rank-num" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
        <div class="rank-info">
          <span class="rank-title">{{ item.title }}</span>
          <span class="rank-score">热度 {{ Number(item.score || 0).toFixed(1) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'

const list = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await request.get('/rank/hot', { params: { n: 15 } })
    if (res.data) {
      list.value = res.data
    } else if (Array.isArray(res)) {
      list.value = res
    }
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hot-rank {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.rank-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  border-bottom: 1px solid var(--border);
  background: linear-gradient(180deg, #f8f9ff, var(--surface));
}
.rank-header-icon {
  color: #ff8a00;
  font-size: 16px;
}
.rank-loading, .rank-empty {
  padding: 16px;
  color: var(--text-3);
  font-size: 13px;
}
.rank-list {
  padding: 4px 0;
}
.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.rank-item:hover {
  background: var(--surface-2);
}
.rank-num {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-3);
  border-radius: 6px;
  flex-shrink: 0;
  font-style: italic;
}
.rank-1, .rank-2, .rank-3 {
  color: #fff;
  border-radius: 6px;
}
.rank-1 { background: linear-gradient(135deg, #fe2d46, #ff6b4a); box-shadow: 0 2px 6px rgba(254, 45, 70, 0.35); }
.rank-2 { background: linear-gradient(135deg, #ff6a00, #ff9a3c); box-shadow: 0 2px 6px rgba(255, 106, 0, 0.3); }
.rank-3 { background: linear-gradient(135deg, #faa90e, #ffd166); box-shadow: 0 2px 6px rgba(250, 169, 14, 0.3); }
.rank-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.rank-title {
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.rank-title:hover {
  color: var(--brand);
}
.rank-score {
  font-size: 12px;
  color: #ff8a00;
  white-space: nowrap;
  font-weight: 500;
}
</style>
