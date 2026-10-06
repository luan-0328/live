<template>
  <div class="comment-item" :style="{ paddingLeft: isChild ? '48px' : '0' }">
    <div class="comment-main">
      <el-avatar :size="32" :src="avatarUrl" />
      <div class="comment-content">
        <div class="comment-header">
          <span class="comment-author">{{ comment.status === -1 ? '已删除评论' : (comment.author?.nickname || '已注销用户') }}</span>
          <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
        </div>
        <p class="comment-text"><span v-if="comment.replyToAuthor">回复 @{{ comment.replyToAuthor.nickname }}： </span>{{ comment.content }}</p>
        <div class="comment-actions">
          <span v-if="showReply && comment.status === 1" class="action-btn" @click="$emit('reply', comment)">
            回复
          </span>
          <span
            v-if="canDelete"
            class="action-btn delete"
            @click="$emit('delete', comment.id)"
          >
            删除
          </span>
          <span v-if="comment.status === 1" class="action-btn report" @click="$emit('report', comment)">
            举报
          </span>
        </div>
      </div>
    </div>
    <div v-if="comment.children?.length" class="comment-children">
      <CommentItem
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        :is-child="true"
        :show-reply="comment.status === 1"
        @reply="$emit('reply', $event)"
        @delete="$emit('delete', $event)"
        @report="$emit('report', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../stores/user'
import { DEFAULT_AVATAR } from '../constants'
import { formatTime } from '../composables/useFormat'

const userStore = useUserStore()

const props = defineProps({
  comment: { type: Object, required: true },
  isChild: { type: Boolean, default: false },
  showReply: { type: Boolean, default: true }
})

defineEmits(['reply', 'delete', 'report'])

const avatarUrl = computed(() => {
  return props.comment.author?.avatar || DEFAULT_AVATAR
})

const canDelete = computed(() => {
  return props.comment.status === 1 && userStore.isLogin && (
    userStore.user?.userId === props.comment.author?.userId ||
    userStore.isAdmin
  )
})
</script>

<style scoped>
.comment-item {
  margin-bottom: 4px;
}
.comment-main {
  display: flex;
  gap: 10px;
  padding: 12px 0;
}
.comment-content {
  flex: 1;
  min-width: 0;
}
.comment-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.comment-author {
  font-size: 13px;
  color: var(--brand);
  font-weight: 500;
}
.comment-time {
  font-size: 12px;
  color: var(--text-3);
}
.comment-text {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
  word-break: break-all;
}
.comment-actions {
  margin-top: 6px;
  display: flex;
  gap: 12px;
}
.action-btn {
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  transition: color 0.15s;
}
.action-btn:hover {
  color: var(--brand);
}
.action-btn.delete:hover {
  color: #f56c6c;
}
.action-btn.report:hover {
  color: #e6a23c;
}
.comment-children {
  background: var(--surface-2);
  border-radius: var(--radius-sm);
  padding: 0 14px;
  margin-top: 2px;
  border: 1px solid var(--border);
}
</style>
