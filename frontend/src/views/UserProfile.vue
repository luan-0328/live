<template>
  <div class="profile-page" v-if="profile">
    <div class="profile-header">
      <el-avatar :size="72" :src="avatarUrl" />
      <div class="profile-info">
        <h2 class="profile-name">{{ profile.nickname || '匿名' }}</h2>
        <div class="profile-stats">
          <span>帖子 {{ profile.postCount || 0 }}</span>
          <span>粉丝 {{ profile.followerCount || 0 }}</span>
          <span>关注 {{ profile.followingCount || 0 }}</span>
        </div>
      </div>
      <div class="profile-action">
        <el-button
          v-if="userStore.isLogin && userStore.user?.userId !== userId"
          :type="isFollowing ? 'default' : 'primary'"
          size="small"
          :loading="followingBusy"
          @click="toggleFollow"
        >
          {{ isFollowing ? '已关注' : '关注' }}
        </el-button>
        <el-button
          v-if="userStore.isLogin && userStore.user?.userId !== userId"
          size="small"
          plain
          type="danger"
          @click="reportUser"
        >
          举报
        </el-button>
      </div>
    </div>

    <ReportDialog ref="reportDialog" />

    <div class="profile-posts">
      <h3 class="section-title">TA的帖子</h3>
      <div v-if="loadingPosts" class="posts-loading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="userPosts.length === 0" class="posts-empty">
        <el-empty description="暂无帖子" />
      </div>
      <template v-else>
        <PostCard v-for="post in userPosts" :key="post.postId" :post="post" />
        <el-pagination v-if="postTotal > 20" v-model:current-page="postPage" :page-size="20"
          :total="postTotal" layout="prev, pager, next" @current-change="loadUserPosts" />
      </template>
    </div>
  </div>
  <div v-else-if="!loading" class="not-found">
    <el-empty description="用户不存在" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getUserProfile, followUser, unfollowUser } from '../api/user'
import { getPostList } from '../api/post'
import PostCard from '../components/PostCard.vue'
import ReportDialog from '../components/ReportDialog.vue'
import { latestRequest } from '../utils/latestRequest'
import { DEFAULT_AVATAR } from '../constants'

const route = useRoute()
const userStore = useUserStore()

const userId = computed(() => parseInt(route.params.id))
const profile = ref(null)
const loading = ref(true)
const isFollowing = ref(false)
const userPosts = ref([])
const loadingPosts = ref(false)
const reportDialog = ref()
const postPage = ref(1)
const postTotal = ref(0)
const followingBusy = ref(false)
const requests = latestRequest()
onUnmounted(requests.cancel)

const avatarUrl = computed(() => {
  return profile.value?.avatar || DEFAULT_AVATAR
})

onMounted(async () => {
  await loadProfile()
  loadUserPosts()
})

async function loadProfile() {
  loading.value = true
  try {
    const res = await getUserProfile(userId.value)
    profile.value = res.data || res
    isFollowing.value = !!profile.value?.isFollowing
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function loadUserPosts() {
  const id = requests.start()
  loadingPosts.value = true
  try {
    const res = await getPostList({ authorId: userId.value, sort: 'latest', page: postPage.value, size: 20 })
    const data = res.data || res
    if (!requests.isCurrent(id)) return
    userPosts.value = data.records || []
    postTotal.value = data.total || 0
  } catch {
    if (requests.isCurrent(id)) { userPosts.value = []; postTotal.value = 0 }
  } finally {
    if (requests.isCurrent(id)) loadingPosts.value = false
  }
}

function reportUser() {
  if (!userStore.isLogin) return
  reportDialog.value?.open('user', userId.value)
}

async function toggleFollow() {
  if (!userStore.isLogin || followingBusy.value) return
  followingBusy.value = true
  try {
    if (isFollowing.value) {
      await unfollowUser(userId.value)
      isFollowing.value = false
      if (profile.value) profile.value.followerCount = Math.max(0, (profile.value.followerCount || 1) - 1)
    } else {
      await followUser(userId.value)
      isFollowing.value = true
      if (profile.value) profile.value.followerCount = (profile.value.followerCount || 0) + 1
    }
    await userStore.fetchUserInfo()
  } catch {} finally { followingBusy.value = false }
}
</script>

<style scoped>
.profile-header {
  background: linear-gradient(165deg, #ffffff 0%, #eef2ff 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 28px 32px;
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}
.profile-info {
  flex: 1;
}
.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}
.profile-stats {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: var(--text-2);
}
.profile-stats span {
  padding: 4px 0;
}
.profile-posts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  padding: 2px 2px 4px;
}
.posts-loading {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.posts-empty {
  padding: 40px 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.not-found {
  padding: 80px 0;
}
</style>
