<script setup>
/**
 * 註冊頁。註冊成功後自動登入，再回到 ?redirect= 指定的頁面（例如結帳）。
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { register } from '@/api/auth'
import { safeRedirect } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'
import { rules as baseRules, toFieldErrorMap } from '@/utils/validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const form = reactive({ email: '', password: '', confirmPassword: '', name: '', phone: '' })
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

const submitting = ref(false)
const errorMessage = ref('')
// 後端回傳的欄位錯誤（例如 Email 已註冊），顯示在對應欄位下方
const serverErrors = ref({})

async function submit() {
  errorMessage.value = ''
  serverErrors.value = {}
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  try {
    await register(form)
    await auth.login({ email: form.email, password: form.password })
    ElMessage.success('註冊成功，已自動登入')
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    if (e.code === 'EMAIL_ALREADY_EXISTS') {
      serverErrors.value = { email: e.message }
    } else if (e.fieldErrors?.length) {
      serverErrors.value = toFieldErrorMap(e)
    } else {
      errorMessage.value = e.message
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <h1>註冊</h1>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="alert" />

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="Email" prop="email" :error="serverErrors.email">
        <el-input v-model="form.email" type="email" autocomplete="email" inputmode="email" />
      </el-form-item>
      <el-form-item label="密碼" prop="password" :error="serverErrors.password">
        <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
        <div class="field-hint">8 到 72 個英文字母、數字或符號</div>
      </el-form-item>
      <el-form-item label="確認密碼" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-form-item label="姓名" prop="name" :error="serverErrors.name">
        <el-input v-model="form.name" autocomplete="name" maxlength="50" />
      </el-form-item>
      <el-form-item label="手機（選填）" prop="phone" :error="serverErrors.phone">
        <el-input v-model="form.phone" type="tel" autocomplete="tel" inputmode="numeric" maxlength="10" />
      </el-form-item>
      <el-button type="primary" size="large" native-type="submit" :loading="submitting" class="submit">
        註冊
      </el-button>
    </el-form>

    <p class="switch">
      已經有帳號？
      <RouterLink :to="{ name: 'login', query: route.query.redirect ? { redirect: route.query.redirect } : {} }">
        登入
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth-page {
  max-width: 400px;
  margin: 0 auto;
}

h1 {
  font-size: 1.4rem;
}

.alert {
  margin-bottom: 16px;
}

.field-hint {
  color: var(--color-muted);
  font-size: 0.8rem;
  line-height: 1.4;
}

.submit {
  width: 100%;
  margin-top: 8px;
}

.switch {
  text-align: center;
  color: var(--color-muted);
}
</style>
