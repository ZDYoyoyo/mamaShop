<script setup>
/**
 * 新增管理員（例如幫另一位家人開帳號）。
 * 建立後請對方用這組密碼登入，再到「管理員」用「重設密碼」改成自己的密碼（B14 的流程）。
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { createAdmin } from '@/api/admin/accounts'
import { rules as baseRules, toFieldErrorMap } from '@/utils/validation'

const props = defineProps({
  open: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'created'])

const formRef = ref()
const form = reactive({ email: '', name: '', phone: '', password: '', confirmPassword: '' })
const rules = {
  ...baseRules,
  confirmPassword: [
    { required: true, message: '請再輸入一次密碼', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === form.password ? callback() : callback(new Error('兩次輸入的密碼不一樣')),
      trigger: 'blur',
    },
  ],
}
const serverErrors = ref({})
const submitError = ref('')
const submitting = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    Object.assign(form, { email: '', name: '', phone: '', password: '', confirmPassword: '' })
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
    const created = await createAdmin({
      email: form.email.trim(),
      name: form.name.trim(),
      phone: form.phone.trim() || null,
      password: form.password,
    })
    ElMessage.success(`已新增管理員「${created.name}」`)
    emit('created', created)
    emit('close')
  } catch (e) {
    if (e.fieldErrors?.length) serverErrors.value = toFieldErrorMap(e)
    else if (e.code === 'EMAIL_ALREADY_EXISTS') serverErrors.value = { email: e.message }
    else submitError.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="open" title="新增管理員" width="min(460px, 92vw)" append-to-body @close="emit('close')">
    <p class="hint">新的管理員可以管理商品、訂單，也能新增、停用其他管理員。建立後請對方登入，再用「重設密碼」改成自己的密碼。</p>
    <!-- novalidate：用 Element Plus 的驗證就好，不要讓瀏覽器自己檢查 Email 格式 -->
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" novalidate @submit.prevent="submit">
      <el-form-item label="Email（登入帳號）" prop="email" :error="serverErrors.email">
        <el-input v-model="form.email" type="email" autocomplete="off" />
      </el-form-item>
      <el-form-item label="姓名" prop="name" :error="serverErrors.name">
        <el-input v-model="form.name" maxlength="50" />
      </el-form-item>
      <el-form-item label="手機（選填）" prop="phone" :error="serverErrors.phone">
        <el-input v-model="form.phone" type="tel" maxlength="10" />
      </el-form-item>
      <el-form-item label="初始密碼" prop="password" :error="serverErrors.password">
        <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-form-item label="再輸入一次密碼" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon />
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">新增</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: var(--color-muted);
  font-size: 0.9rem;
  line-height: 1.6;
}
</style>
