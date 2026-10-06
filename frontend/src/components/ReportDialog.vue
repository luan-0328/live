<template>
  <el-dialog v-model="visible" title="举报" width="440px" :close-on-click-modal="false">
    <div class="report-tip">请选择举报原因，管理员审核后处理。</div>
    <el-radio-group v-model="reason" class="reason-list">
      <el-radio v-for="r in presetReasons" :key="r" :value="r">{{ r }}</el-radio>
    </el-radio-group>
    <el-input
      v-if="reason === '其他'"
      v-model="customReason"
      type="textarea"
      :rows="3"
      maxlength="500"
      show-word-limit
      placeholder="请描述具体情况"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="submitting" :disabled="!reasonValid" @click="submit">
        提交举报
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { submitReport } from '../api/report'

const userStore = useUserStore()

const presetReasons = ['垃圾广告', '色情低俗', '人身攻击', '违法违规', '其他']

const visible = ref(false)
const targetType = ref('')
const targetId = ref(null)
const reason = ref('')
const customReason = ref('')
const submitting = ref(false)

const reasonValid = computed(() => {
  if (reason.value === '其他') return customReason.value.trim().length > 0
  return !!reason.value
})

function open(type, id) {
  if (!userStore.isLogin) return
  targetType.value = type
  targetId.value = id
  reason.value = ''
  customReason.value = ''
  visible.value = true
}

async function submit() {
  const finalReason = reason.value === '其他' ? customReason.value.trim() : reason.value
  submitting.value = true
  try {
    await submitReport({
      targetType: targetType.value,
      targetId: targetId.value,
      reason: finalReason
    })
    ElMessage.success('举报已提交，感谢反馈')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.report-tip {
  font-size: 13px;
  color: var(--text-3);
  margin-bottom: 12px;
}
.reason-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
</style>
