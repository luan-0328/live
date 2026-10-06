<template>
  <div class="center-page">
    <div class="center-sidebar">
      <div class="sidebar-card">
        <div class="user-card">
          <el-avatar :size="64" :src="avatarUrl" />
          <div class="user-name">{{ userStore.user?.nickname || '未设置昵称' }}</div>
          <div class="user-meta">
            <span>帖子 {{ userStore.user?.postCount || 0 }}</span>
            <span>粉丝 {{ userStore.user?.followerCount || 0 }}</span>
            <span>关注 {{ userStore.user?.followingCount || 0 }}</span>
          </div>
        </div>
        <div class="sidebar-menu">
          <div class="menu-item" :class="{ active: tab === 'posts' }" @click="tab = 'posts'">
            <el-icon><Document /></el-icon> 我的帖子
          </div>
          <div v-for="entry in [{ key: 'followers', label: '我的粉丝' }, { key: 'following', label: '我的关注' }, { key: 'reports', label: '我的举报' }]"
            :key="entry.key" class="menu-item" :class="{ active: tab === entry.key }" @click="tab = entry.key">
            {{ entry.label }}
          </div>
          <div class="menu-item" :class="{ active: tab === 'profile' }" @click="tab = 'profile'">
            <el-icon><Edit /></el-icon> 编辑资料
          </div>
          <div class="menu-item" :class="{ active: tab === 'phone' }" @click="tab = 'phone'">
            <el-icon><Iphone /></el-icon> 修改手机
          </div>
          <div class="menu-item" :class="{ active: tab === 'password' }" @click="tab = 'password'">
            <el-icon><Lock /></el-icon> 修改密码
          </div>
          <div v-if="userStore.isAdmin" class="menu-item" @click="$router.push('/admin')">
            <el-icon><Setting /></el-icon> 管理后台
          </div>
          <div class="menu-item danger" @click="openDeleteDialog">
            <el-icon><Delete /></el-icon> 注销账号
          </div>
        </div>
      </div>
    </div>

    <div class="center-main">
      <!-- 我的帖子 -->
      <div v-if="tab === 'posts'" class="center-list">
        <h3 class="list-title">我的帖子</h3>
        <div v-if="loadingPosts" class="card-loading">
          <el-skeleton :rows="4" animated />
        </div>
        <div v-else-if="myPosts.length === 0" class="card-empty">
          <el-empty description="还没有发过帖子" />
        </div>
        <template v-else>
          <PostCard v-for="post in myPosts" :key="post.postId" :post="post" />
          <el-pagination v-if="postTotal > 20" v-model:current-page="postPage" :page-size="20"
            :total="postTotal" layout="prev, pager, next" @current-change="loadMyPosts" />
        </template>
      </div>

      <div v-if="['followers', 'following', 'reports'].includes(tab)" class="center-card">
        <h3 class="card-title">{{ tab === 'reports' ? '我的举报' : (tab === 'followers' ? '我的粉丝' : '我的关注') }}</h3>
        <el-skeleton v-if="recordsLoading" :rows="4" animated />
        <el-empty v-else-if="!records.length" description="暂无记录" />
        <template v-else>
          <el-table v-if="tab === 'reports'" :data="records">
            <el-table-column prop="targetType" label="对象类型" width="100" />
            <el-table-column prop="targetId" label="对象ID" width="90" />
            <el-table-column prop="reason" label="举报原因" show-overflow-tooltip />
            <el-table-column label="状态" width="100"><template #default="{ row }">
              {{ row.status === 0 ? '待处理' : (row.status === 1 ? '违规成立' : '已驳回') }}
            </template></el-table-column>
            <el-table-column prop="handleNote" label="处理备注" show-overflow-tooltip />
          </el-table>
          <el-table v-else :data="records">
            <el-table-column label="用户"><template #default="{ row }">
              <router-link v-if="row.status === 1" :to="`/user/${row.id}`">{{ row.nickname }}</router-link>
              <span v-else>{{ row.nickname }}（账号不可用）</span>
            </template></el-table-column>
            <el-table-column prop="postCount" label="帖子数" width="100" />
            <el-table-column v-if="tab === 'following'" label="操作" width="120"><template #default="{ row }">
              <el-button link :loading="recordsBusy === row.id" @click="cancelFollow(row)">取消关注</el-button>
            </template></el-table-column>
          </el-table>
          <el-pagination v-if="recordsTotal > 20" v-model:current-page="recordsPage" :page-size="20"
            :total="recordsTotal" layout="prev, pager, next" @current-change="loadRecords" />
        </template>
      </div>

      <!-- 编辑资料 -->
      <div v-if="tab === 'profile'" class="center-card">
        <h3 class="card-title">编辑资料</h3>
        <el-form :model="profileForm" label-width="70px" class="profile-form">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" maxlength="12" placeholder="输入新昵称" />
          </el-form-item>
          <el-form-item label="头像">
            <div class="avatar-upload">
              <el-avatar :size="72" :src="profileForm.avatar || avatarUrl" />
              <el-upload
                :show-file-list="false"
                :http-request="uploadAvatar"
                :before-upload="beforeAvatarUpload"
                accept="image/jpeg,image/png,image/gif,image/webp"
              >
                <el-button size="small" type="primary">选择图片</el-button>
              </el-upload>
            </div>
            <div class="avatar-hint">支持 JPG/PNG/GIF/WebP，不超过 5MB</div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 修改手机 -->
      <div v-if="tab === 'phone'" class="center-card">
        <h3 class="card-title">修改手机号</h3>
        <el-form ref="phoneFormRef" :model="phoneForm" :rules="phoneRules" label-width="90px">
          <el-form-item label="新手机号" prop="newPhone">
            <el-input v-model="phoneForm.newPhone" maxlength="11" placeholder="输入新手机号" />
          </el-form-item>
          <el-form-item label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="phoneForm.code" maxlength="6" placeholder="验证码" />
              <el-button :disabled="sendingCode || countdown > 0" @click="sendPhoneCode">
                {{ countdown > 0 ? `${countdown}s后重发` : (sendingCode ? '发送中' : '获取验证码') }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPhone" @click="savePhone">确认修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 修改密码 -->
      <div v-if="tab === 'password'" class="center-card">
        <h3 class="card-title">修改密码</h3>
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="8~64位，含字母和数字" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPassword" @click="savePassword">确认修改</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-dialog v-model="deleteVisible" title="注销账号" width="440px" :close-on-click-modal="false">
      <div class="delete-warning">
        <p>注销后该账号将无法登录，所有帖子、评论、关注关系、通知等数据将被清理，且不可恢复。</p>
        <p class="delete-tip">确定要继续吗？</p>
      </div>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="deletingAccount" @click="confirmDelete">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { updateProfile, updatePhone, updatePassword, deleteAccount, getFollowers, getFollowing, unfollowUser } from '../api/user'
import { getMyReports } from '../api/report'
import { latestRequest } from '../utils/latestRequest'
import { imageError } from '../utils/images'
import { sendCode } from '../api/auth'
import { getPostList } from '../api/post'
import { uploadImage } from '../api/upload'
import PostCard from '../components/PostCard.vue'
import { Document, Edit, Iphone, Lock, Setting, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { DEFAULT_AVATAR } from '../constants'

const router = useRouter()

const userStore = useUserStore()

const tab = ref('posts')

const avatarUrl = computed(() => {
  return userStore.user?.avatar || DEFAULT_AVATAR
})

// 我的帖子
const myPosts = ref([])
const loadingPosts = ref(false)
const postPage = ref(1)
const postTotal = ref(0)
const listRequests = latestRequest()
const postsRequests = latestRequest()
const records = ref([])
const recordsTotal = ref(0)
const recordsPage = ref(1)
const recordsLoading = ref(false)
const recordsBusy = ref(null)
const countdown = ref(0)
let codeTimer
onUnmounted(() => { listRequests.cancel(); postsRequests.cancel(); clearInterval(codeTimer) })
watch(tab, value => {
  if (value === 'posts') loadMyPosts()
  if (['followers', 'following', 'reports'].includes(value)) { recordsPage.value = 1; loadRecords() }
})

onMounted(async () => {
  await userStore.fetchUserInfo()
  if (!userStore.isLogin) return
  if (tab.value === 'posts') loadMyPosts()
  profileForm.nickname = userStore.user?.nickname || ''
  profileForm.avatar = userStore.user?.avatar || ''
})

const profileForm = reactive({
  nickname: '',
  avatar: ''
})
const savingProfile = ref(false)

async function saveProfile() {
  const nickname = profileForm.nickname.trim()
  if (nickname.length < 2 || nickname.length > 12) return ElMessage.warning('昵称长度须为2～12字')
  if (savingProfile.value) return
  savingProfile.value = true
  try {
    await updateProfile({ nickname, avatar: profileForm.avatar })
    profileForm.nickname = nickname
    await userStore.fetchUserInfo()
    ElMessage.success('保存成功')
  } finally {
    savingProfile.value = false
  }
}

function beforeAvatarUpload(file) {
  const error = imageError(file)
  if (error) ElMessage.warning(error)
  return !error
}

async function uploadAvatar(options) {
  try {
    const res = await uploadImage(options.file, 'avatar')
    const data = res.data || res
    profileForm.avatar = data.url
    ElMessage.success('头像已上传')
  } catch {}
}

// 手机号修改
const phoneFormRef = ref()
const phoneForm = reactive({ newPhone: '', code: '' })
const phoneRules = {
  newPhone: [{ required: true, pattern: /^1\d{10}$/, message: '请输入正确手机号' }],
  code: [{ required: true, pattern: /^\d{6}$/, message: '请输入6位数字验证码' }]
}
const sendingCode = ref(false)
const savingPhone = ref(false)

// 密码修改
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 8, max: 64, message: '密码长度需为 8~64 位' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]+$/, message: '密码需至少含一个字母和一个数字，且只能用字母、数字和常见符号' }
  ]
}
const savingPassword = ref(false)

async function savePassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await updatePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码修改成功')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  } finally {
    savingPassword.value = false
  }
}

async function sendPhoneCode() {
  if (sendingCode.value || countdown.value > 0) return
  if (!phoneForm.newPhone.match(/^1\d{10}$/)) {
    ElMessage.warning('请输入正确手机号')
    return
  }
  sendingCode.value = true
  try {
    await sendCode(phoneForm.newPhone)
    countdown.value = 60
    clearInterval(codeTimer)
    codeTimer = setInterval(() => { if (--countdown.value <= 0) clearInterval(codeTimer) }, 1000)
    ElMessage.success('验证码已发送')
  } finally {
    sendingCode.value = false
  }
}

const deleteVisible = ref(false)
const deletingAccount = ref(false)

function openDeleteDialog() {
  if (!userStore.isLogin) return
  deleteVisible.value = true
}

async function confirmDelete() {
  deletingAccount.value = true
  try {
    await deleteAccount()
    ElMessage.success('账号已注销')
    userStore.logout()
    router.push('/')
  } finally {
    deletingAccount.value = false
  }
}

async function savePhone() {
  const valid = await phoneFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingPhone.value = true
  try {
    await updatePhone({ newPhone: phoneForm.newPhone, code: phoneForm.code })
    await userStore.fetchUserInfo()
    ElMessage.success('修改成功')
    phoneForm.newPhone = ''
    phoneForm.code = ''
  } finally {
    savingPhone.value = false
  }
}

async function loadMyPosts() {
  const id = postsRequests.start()
  if (!userStore.user?.userId) { myPosts.value = []; postTotal.value = 0; return }
  loadingPosts.value = true
  try {
    const res = await getPostList({ authorId: userStore.user.userId, sort: 'latest', page: postPage.value, size: 20 })
    const data = res.data || res
    if (!postsRequests.isCurrent(id)) return
    myPosts.value = data.records || []
    postTotal.value = data.total || 0
  } catch {
    if (postsRequests.isCurrent(id)) { myPosts.value = []; postTotal.value = 0 }
  } finally {
    if (postsRequests.isCurrent(id)) loadingPosts.value = false
  }
}
async function loadRecords() {
  const id = listRequests.start()
  const currentTab = tab.value
  const fetch = { followers: getFollowers, following: getFollowing, reports: getMyReports }[currentTab]
  if (!fetch) return
  recordsLoading.value = true
  try {
    const res = await fetch({ page: recordsPage.value, size: 20 })
    if (!listRequests.isCurrent(id) || tab.value !== currentTab) return
    records.value = res.data?.records || []
    recordsTotal.value = res.data?.total || 0
  } catch {
    if (listRequests.isCurrent(id)) { records.value = []; recordsTotal.value = 0 }
  } finally { if (listRequests.isCurrent(id)) recordsLoading.value = false }
}

async function cancelFollow(row) {
  if (recordsBusy.value != null) return
  recordsBusy.value = row.id
  try {
    await unfollowUser(row.id)
    await userStore.fetchUserInfo()
    await loadRecords()
    if (!records.value.length && recordsPage.value > 1) { recordsPage.value--; await loadRecords() }
  } catch {} finally { recordsBusy.value = null }
}
</script>

<style scoped>
.center-page {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.center-sidebar {
  width: 240px;
  flex-shrink: 0;
}
.center-main {
  flex: 1;
  min-width: 0;
}
.sidebar-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.user-card {
  text-align: center;
  padding: 26px 20px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(165deg, #f8f9ff, var(--surface));
}
.user-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin: 12px 0 8px;
}
.user-meta {
  display: flex;
  justify-content: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-3);
}
.sidebar-menu {
  padding: 8px 0;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}
.menu-item:hover {
  background: var(--surface-2);
  color: var(--brand);
}
.menu-item.active {
  color: var(--brand);
  background: var(--brand-light);
  border-left-color: var(--brand);
  font-weight: 500;
}
.menu-item.danger {
  color: #f56c6c;
}
.menu-item.danger:hover {
  background: #fef0f0;
  color: #f56c6c;
}
.delete-warning {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.8;
}
.delete-warning .delete-tip {
  margin-top: 8px;
  color: #f56c6c;
  font-weight: 500;
}
.center-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 26px;
}
.center-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.list-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
  padding: 2px 2px 4px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.card-loading {
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.card-empty {
  padding: 40px 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.profile-form {
  max-width: 400px;
}
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar-hint {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}
.code-row {
  display: flex;
  gap: 8px;
}
</style>
