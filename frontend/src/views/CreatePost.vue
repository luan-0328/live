<template>
  <div class="create-page">
    <div class="create-card">
      <h2 class="page-title">{{ editing ? '编辑帖子' : '发布帖子' }}</h2>
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="initializing || submitting" label-width="70px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="标题，最多50字" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width:200px">
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="说点什么吧..."
            maxlength="10000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="位置">
          <el-button :loading="locating" @click="useCurrentLocation">使用当前位置</el-button>
          <el-button v-if="form.latitude != null" link @click="form.longitude = null; form.latitude = null">移除位置</el-button>
          <span v-if="form.latitude != null" style="margin-left:12px">已添加位置</span>
        </el-form-item>
        <el-form-item label="图片">
          <div class="upload-area">
            <el-upload
              action="#"
              accept="image/jpeg,image/png,image/gif,image/webp"
              :on-exceed="() => ElMessage.warning('最多9张图片')"
              list-type="picture-card"
              :auto-upload="false"
              :limit="9"
              :on-change="handleFileChange"
              :on-remove="handleFileChange"
              :file-list="fileList"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
            <span class="upload-tip">支持 JPG/PNG/GIF/WebP，最多9张，单张不超过5MB</span>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
            {{ editing ? '保存修改' : '发布帖子' }}
          </el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPost, updatePost, getPostDetail } from '../api/post'
import { getCategories } from '../api/category'
import { useUserStore } from '../stores/user'
import { imageError } from '../utils/images'
import { uploadImage } from '../api/upload'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const editing = !!route.params.id
const userStore = useUserStore()
const initializing = ref(true)
const formRef = ref(null)
const submitting = ref(false)
const locating = ref(false)
const categories = ref([])
const fileList = ref([])

const form = reactive({
  title: '',
  categoryId: '',
  content: '',
  longitude: null,
  latitude: null
})

const rules = {
  title: [
    { required: true, message: '请输入标题' },
    { max: 50, message: '标题不超过50字' }
  ],
  categoryId: [
    { required: true, message: '请选择分类' }
  ],
  content: [
    { required: true, message: '请输入内容' }
  ]
}

onMounted(async () => {
  try {
    const res = await getCategories()
    categories.value = res.data || res || []
    if (editing) {
      const detail = (await getPostDetail(route.params.id)).data
      if (detail.authorId !== userStore.user?.userId) {
        ElMessage.warning('只能编辑自己的帖子')
        return router.replace(`/post/${route.params.id}`)
      }
      Object.assign(form, { title: detail.title, content: detail.content, categoryId: detail.categoryId,
        longitude: detail.longitude ?? null, latitude: detail.latitude ?? null })
      fileList.value = (detail.images || []).map((url, i) => ({ name: `图片${i + 1}`, url, uid: i }))
    }
  } catch {
    if (editing) router.replace(`/post/${route.params.id}`)
  } finally { initializing.value = false }
})

async function useCurrentLocation() {
  if (!navigator.geolocation) return ElMessage.warning('当前浏览器不支持定位')
  locating.value = true
  try {
    const result = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 10000 }))
    form.longitude = result.coords.longitude
    form.latitude = result.coords.latitude
  } catch { ElMessage.warning('无法获取位置，请允许定位并通过HTTPS访问') }
  finally { locating.value = false }
}

function handleFileChange(file, list) {
  fileList.value = list.filter(item => {
    const error = item.raw ? imageError(item.raw) : ''
    if (error) ElMessage.warning(error)
    return !error
  }).slice(0, 9)
}

async function handleSubmit() {
  if (initializing.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 上传失败时停止保存，避免成功提示掩盖图片丢失。成功的URL保留供重试复用。
    const imageUrls = []
    for (const f of fileList.value) {
      if (f.raw) {
        try {
          const res = await uploadImage(f.raw, 'post')
          const data = res.data || res
          f.url = data.url
          f.raw = null
          imageUrls.push(f.url)
        } catch {
          throw new Error(`图片"${f.name}"上传失败，请重试`)
        }
      } else if (f.url) {
        imageUrls.push(f.url)
      }
    }
    const data = {
      title: form.title,
      content: form.content,
      categoryId: form.categoryId,
      images: imageUrls,
      longitude: form.longitude,
      latitude: form.latitude
    }
    if (editing) await updatePost(route.params.id, data)
    else await createPost(data)
    ElMessage.success(editing ? '修改成功' : '发布成功')
    router.push(editing ? `/post/${route.params.id}` : '/')
  } catch (e) {
    // 错误已在 request 拦截器中弹出提示，这里不做重复处理
    console.error('发布失败:', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-page {
  max-width: 760px;
  margin: 0 auto;
}
.create-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  padding: 32px;
}
.page-title {
  font-size: 19px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.upload-area {
  width: 100%;
}
.upload-tip {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}
</style>
