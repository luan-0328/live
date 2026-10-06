<template>
  <div class="detail-page" v-if="post">
    <div class="detail-main">
      <!-- 帖子内容 -->
      <div class="post-section">
        <div class="post-author-bar">
          <el-avatar :size="44" :src="authorAvatar" />
          <div class="author-detail">
            <router-link :to="`/user/${post.author?.userId}`" class="author-name">
              {{ post.author?.nickname || '匿名' }}
            </router-link>
            <div class="post-time">{{ post.createdAt }} · {{ post.viewCount || 0 }} 次浏览</div>
          </div>
        </div>

        <h1 class="post-title">{{ post.title }}</h1>
        <div class="post-content">{{ post.content }}</div>

        <div v-if="imageList.length > 0" class="post-images">
          <el-image
            v-for="(img, i) in imageList"
            :key="i"
            :src="img"
            :preview-src-list="imageList"
            :initial-index="i"
            preview-teleported
            fit="contain"
            class="detail-img"
          />
        </div>

        <div v-if="post.categoryName" class="post-category">
          <el-tag size="small">{{ post.categoryName }}</el-tag>
        </div>

        <div class="post-actions-bar">
          <span class="action-item" :class="{ active: liked }" @click="toggleLike">
            <el-icon><Goods /></el-icon>
            {{ liked ? '已赞' : '点赞' }} {{ post.likeCount || '' }}
          </span>
          <span class="action-item" :class="{ active: favorited }" @click="toggleFavorite">
            <el-icon><Star /></el-icon>
            {{ favorited ? '已收藏' : '收藏' }}
          </span>
          <router-link v-if="isAuthor" :to="`/post/${post.id}/edit`" class="action-item">编辑</router-link>
          <span v-if="canDelete" class="action-item danger" @click="handleDelete">
            <el-icon><Delete /></el-icon>
            删除
          </span>
          <span class="action-item" @click="reportPost">
            <el-icon><WarningFilled /></el-icon>
            举报
          </span>
        </div>
      </div>

      <!-- 评论区 -->
      <div class="comments-section">
        <h3 class="comments-title">评论 ({{ post.commentCount || 0 }})</h3>
        <div v-if="userStore.isLogin" class="comment-form">
          <div v-if="replyTarget" class="reply-target">
            回复 @{{ replyTarget.author?.nickname || '用户' }}
            <el-button link size="small" @click="replyTarget = null">取消回复</el-button>
          </div>
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            placeholder="说点什么..."
            maxlength="500"
            show-word-limit
          />
          <div class="comment-submit">
            <el-button type="primary" size="small" :loading="submitting" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </div>
        <div v-else class="comment-login-tip">
          <router-link to="/login">登录后即可评论</router-link>
        </div>

        <div v-if="commentsLoading" class="comments-loading">
          <el-skeleton :rows="3" animated />
        </div>
        <div v-else-if="comments.length === 0" class="comments-empty">暂无评论</div>
        <template v-else>
          <CommentItem
            v-for="c in comments"
            :key="c.id"
            :comment="c"
            @reply="handleReply"
            @delete="handleDeleteComment"
            @report="handleReportComment"
          />
          <div v-if="commentTotal > 20" class="comments-pagination">
            <el-pagination
              v-model:current-page="commentPage"
              :page-size="20"
              :total="commentTotal"
              layout="prev, pager, next"
              background
              small
              @current-change="loadComments"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 右侧 -->
    <aside class="detail-sidebar">
      <HotRank />
    </aside>

    <ReportDialog ref="reportDialog" />
  </div>
  <div v-else-if="!loading" class="not-found">
    <el-empty description="帖子不存在或已被删除" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getPostDetail, likePost, unlikePost, favoritePost, unfavoritePost, deletePost } from '../api/post'
import { forceDeletePost, forceDeleteComment } from '../api/admin'
import { getComments, addComment, deleteComment } from '../api/comment'
import CommentItem from '../components/CommentItem.vue'
import HotRank from '../components/HotRank.vue'
import ReportDialog from '../components/ReportDialog.vue'
import { Goods, Star, Delete, WarningFilled } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { DEFAULT_AVATAR } from '../constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const loading = ref(true)
const liked = ref(false)
const favorited = ref(false)
const liking = ref(false)
const favoriting = ref(false)
const reportDialog = ref()

const comments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentsLoading = ref(false)
const commentContent = ref('')
const replyTarget = ref(null)
const submitting = ref(false)
const cancelled = ref(false)
onUnmounted(() => { cancelled.value = true })

const isAuthor = computed(() => userStore.user?.userId === post.value?.authorId)

const authorAvatar = computed(() => {
  return post.value?.author?.avatar || DEFAULT_AVATAR
})

const imageList = computed(() => {
  const imgs = post.value?.images
  if (!imgs) return []
  if (Array.isArray(imgs)) return imgs
  try { return JSON.parse(imgs) } catch { return [] }
})

const canDelete = computed(() => {
  if (!userStore.isLogin) return false
  return userStore.user?.userId === post.value?.authorId || userStore.isAdmin
})

onMounted(() => {
  loadPost()
  loadComments()
})

async function loadPost() {
  loading.value = true
  try {
    const res = await getPostDetail(route.params.id)
    if (cancelled.value) return
    const data = res.data || res
    post.value = data
    liked.value = !!data.isLiked
    favorited.value = !!data.isFavorited
  } catch {
    if (cancelled.value) return
    post.value = null
  } finally {
    if (!cancelled.value) loading.value = false
  }
}

async function loadComments(page) {
  commentsLoading.value = true
  commentPage.value = page || 1
  try {
    const res = await getComments(route.params.id, { page: commentPage.value, size: 20 })
    if (cancelled.value) return
    const data = res.data || res
    comments.value = data.records || []
    commentTotal.value = data.total || 0
  } catch {
    if (cancelled.value) return
    comments.value = []
  } finally {
    if (!cancelled.value) commentsLoading.value = false
  }
}

async function toggleLike() {
  if (liking.value) return
  if (!userStore.isLogin) return router.push('/login')
  liking.value = true
  try {
    if (liked.value) {
      await unlikePost(post.value.id)
      liked.value = false
      if (post.value) post.value.likeCount = Math.max(0, (post.value.likeCount || 1) - 1)
    } else {
      await likePost(post.value.id)
      liked.value = true
      if (post.value) post.value.likeCount = (post.value.likeCount || 0) + 1
    }
  } catch {} finally { liking.value = false }
}

async function toggleFavorite() {
  if (favoriting.value) return
  if (!userStore.isLogin) return router.push('/login')
  favoriting.value = true
  try {
    if (favorited.value) {
      await unfavoritePost(post.value.id)
      favorited.value = false
    } else {
      await favoritePost(post.value.id)
      favorited.value = true
    }
  } catch {} finally { favoriting.value = false }
}

async function submitComment() {
  if (submitting.value || !commentContent.value.trim()) return
  submitting.value = true
  try {
    await addComment(post.value.id, {
      content: commentContent.value.trim(),
      parentId: replyTarget.value ? (replyTarget.value.parentId || replyTarget.value.id) : null,
      replyToCommentId: replyTarget.value?.id || null
    })
    commentContent.value = ''
    replyTarget.value = null
    loadComments(commentPage.value)
    if (post.value) post.value.commentCount = (post.value.commentCount || 0) + 1
    ElMessage.success('评论成功')
  } finally {
    submitting.value = false
  }
}

function handleReply(comment) {
  if (!userStore.isLogin) return router.push('/login')
  replyTarget.value = comment
  document.querySelector('.comment-form textarea')?.focus()
}

function reportPost() {
  if (!userStore.isLogin) return router.push('/login')
  reportDialog.value?.open('post', post.value.id)
}

function handleReportComment(comment) {
  if (!userStore.isLogin) return router.push('/login')
  reportDialog.value?.open('comment', comment.id)
}

async function handleDeleteComment(commentId) {
  try {
    await ElMessageBox.confirm('确定删除这条评论？')
    const owner = [...comments.value, ...comments.value.flatMap(root => root.children || [])].find(c => c.id === commentId)
    if (userStore.isAdmin && owner?.authorId !== userStore.user?.userId) await forceDeleteComment(commentId)
    else await deleteComment(commentId)
    if (replyTarget.value?.id === commentId) replyTarget.value = null
    await loadComments(commentPage.value)
    if (!comments.value.length && commentPage.value > 1) await loadComments(commentPage.value - 1)
    if (post.value) post.value.commentCount = Math.max(0, (post.value.commentCount || 1) - 1)
    ElMessage.success('已删除')
  } catch {}
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除这个帖子？')
    if (userStore.isAdmin && !isAuthor.value) await forceDeletePost(post.value.id)
    else await deletePost(post.value.id)
    ElMessage.success('已删除')
    router.push('/')
  } catch {}
}
</script>

<style scoped>
.detail-page {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.detail-main {
  flex: 1;
  min-width: 0;
}
.detail-sidebar {
  width: 300px;
  flex-shrink: 0;
}
.post-section {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 28px 32px;
  margin-bottom: 16px;
}
.post-author-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.author-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--brand);
}
.post-time {
  font-size: 13px;
  color: var(--text-3);
  margin-top: 2px;
}
.post-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
  margin-bottom: 16px;
}
.post-content {
  font-size: 15px;
  color: var(--text);
  line-height: 1.9;
  margin-bottom: 16px;
  word-break: break-all;
  white-space: pre-wrap;
}
.post-images {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.detail-img {
  max-width: 100%;
  max-height: 400px;
  border-radius: var(--radius-sm);
  cursor: zoom-in;
  border: 1px solid var(--border);
}
.post-category {
  margin-bottom: 16px;
}
.post-actions-bar {
  display: flex;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}
.action-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  transition: all 0.2s;
}
.action-item:hover {
  border-color: var(--brand);
  background: var(--brand-light);
  color: var(--brand);
}
.action-item.active {
  color: var(--brand);
  background: var(--brand-light);
  border-color: var(--brand-light);
  font-weight: 500;
}
.action-item.danger:hover {
  color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}
.comments-section {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 24px 32px;
}
.comments-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16px;
}
.comment-form {
  margin-bottom: 20px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 14px;
}
.comment-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.comment-login-tip {
  text-align: center;
  padding: 16px 0;
  font-size: 14px;
}
.comments-loading {
  padding: 16px 0;
}
.comments-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--text-3);
  font-size: 14px;
}
.comments-pagination {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}
.not-found {
  padding: 80px 0;
}
@media (max-width: 960px) {
  .detail-sidebar {
    display: none;
  }
}
</style>
