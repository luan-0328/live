<template>
  <div class="post-card" @click="$router.push(`/post/${post.postId}`)">
    <div class="post-author">
      <el-avatar :size="36" :src="authorAvatar" />
      <div class="author-info">
        <span class="author-name">{{ post.author?.nickname || '匿名' }}</span>
        <span class="post-time">{{ formatTime(post.createTime) }}</span>
      </div>
    </div>
    <div class="post-body">
      <h3 class="post-title">{{ post.title }}</h3>
      <p class="post-excerpt">{{ stripHtml(post.content) }}</p>
      <div v-if="imageList.length > 0" class="post-images">
        <el-image
          v-for="(img, i) in imageList.slice(0, 3)"
          :key="i"
          :src="img"
          :preview-src-list="imageList"
          :initial-index="i"
          fit="cover"
          preview-teleported
          class="post-thumb"
          @click.stop
        />
      </div>
    </div>
    <div class="post-meta">
      <span class="meta-item">
        <el-icon><View /></el-icon>
        {{ post.viewCount || 0 }}
      </span>
      <span class="meta-item">
        <el-icon><ChatLineRound /></el-icon>
        {{ post.commentCount || 0 }}
      </span>
      <span class="meta-item">
        <el-icon><Goods /></el-icon>
        {{ post.likeCount || 0 }}
      </span>
      <span v-if="post.distance != null" class="meta-item distance">
        <el-icon><Location /></el-icon>
        {{ Number(post.distance).toFixed(2) }}km
      </span>
      <span v-if="post.categoryName" class="meta-tag">{{ post.categoryName }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { View, ChatLineRound, Goods, Location } from '@element-plus/icons-vue'
import { DEFAULT_AVATAR } from '../constants'
import { formatTime } from '../composables/useFormat'

const props = defineProps({
  post: { type: Object, required: true }
})

const authorAvatar = computed(() => {
  return props.post.author?.avatar || DEFAULT_AVATAR
})

const imageList = computed(() => {
  return props.post.images || []
})

function stripHtml(html) {
  if (!html) return ''
  return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
}
</script>

<style scoped>
.post-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 18px 20px;
  cursor: pointer;
  transition: var(--transition);
}
.post-card:hover {
  transform: translateY(-2px);
  border-color: #cbd7ff;
  box-shadow: var(--shadow-md);
}
.post-author {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.author-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.author-name {
  color: var(--brand);
  font-weight: 500;
}
.post-time {
  color: var(--text-3);
}
.post-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
  line-height: 1.5;
}
.post-title:hover {
  color: var(--brand);
}
.post-excerpt {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
  margin-bottom: 10px;
}
.post-images {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.post-thumb {
  width: 90px;
  height: 90px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  cursor: zoom-in;
  flex-shrink: 0;
}
.post-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: var(--text-3);
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.distance {
  color: #67c23a;
}
.meta-tag {
  margin-left: auto;
  padding: 2px 10px;
  background: var(--brand-light);
  color: var(--brand);
  border-radius: 999px;
  font-size: 12px;
}
</style>
