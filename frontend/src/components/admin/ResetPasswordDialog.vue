<script setup>
/**
 * 重設管理員密碼。重設後該帳號之前的登入全部失效（B14：比對 token 發行時間與密碼修改時間）。
 * 重設的是自己的密碼時，目前這個登入也會失效 → 父元件收到 reset 後要請使用者重新登入。
 */
import { reactive, ref, watch } from 'vue'
import { resetAdminPassword } from '@/api/admin/accounts'
import { PASSWORD_MESSAGE, PASSWORD_PATTERN, toFieldErrorMap } from '@/utils/validation'

const props = defineProps({
  // 要重設密碼的管理員；null 時關閉
  account: { type: Object, default: null },
  isSelf: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'reset'])

const formRef = ref()
const form = reactive({ newPassword: '', confirmPassword: '' })
const rules = {
  newPassword: [
    { required: true, message: '請輸入新密碼', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '請再輸入一次新密碼', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === form.newPassword ? callback() : callback(new Error('兩次輸入的密碼不一樣')),
      trigger: 'blur',
    },
  ],
}
const serverErrors = ref({})
const submitError = ref('')
const submitting = ref(false)

watch(
  () => props.account,
  (account) => {
    if (!account) return
    Object.assign(form, { newPassword: '', confirmPassword: '' })
    serverErrors.value = {}
    submitError.value = ''
    formRef.value?.clearValidate()
  },
)

async function submit() {
  if (submitting.value) return
  serverErrors.value = {}
  submitError.value = ''
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  try {
    const updated = await resetAdminPassword(props.account.id, form.newPassword)
    emit('reset', updated)
    emit('close')
  } catch (e) {
    if (e.fieldErrors?.length) serverErrors.value = toFieldErrorMap(e)
    else submitError.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="account !== null"
    :title="account ? `重設「${account.name}」的密碼` : ''"
    width="min(440px, 92vw)"
    append-to-body
    @close="emit('close')"
  >
    <el-alert
      :title="isSelf ? '這是你自己的帳號：重設後要用新密碼重新登入' : '重設後，這個帳號在所有裝置上的登入都會失效，要用新密碼重新登入'"
      type="warning"
      :closable="false"
      show-icon
      class="warning"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" novalidate @submit.prevent="submit">
      <el-form-item label="新密碼" prop="newPassword" :error="serverErrors.newPassword">
        <el-input v-model="form.newPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-form-item label="再輸入一次新密碼" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon />
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">重設密碼</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.warning {
  margin-bottom: 16px;
}
</style>
