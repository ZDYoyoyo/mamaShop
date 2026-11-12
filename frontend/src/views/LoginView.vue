<script setup>
/**
 * 登入頁。登入成功後回到 ?redirect= 指定的頁面（例如被要求登入前正在看的頁面），沒有就回首頁。
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { safeRedirect } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'
import { rules as allRules } from '@/utils/validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const form = reactive({ email: '', password: '' })
// 登入只檢查有沒有填；密碼格式不在這裡檢查（不要透露密碼規則給嘗試登入的人）
const rules = { email: allRules.email, password: [{ required: true, message: '請輸入密碼', trigger: 'blur' }] }
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  // validate() 不通過時會丟出例外並在表單上標出錯誤，這裡只要停止送出
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  try {
    await auth.login(form)
    ElMessage.success(`歡迎回來，${auth.user.name}`)
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <h1>登入</h1>
    <p v-if="route.query.redirect" class="hint">請先登入再繼續</p>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="alert" />

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="Email" prop="email">
        <el-input v-model="form.email" type="email" autocomplete="email" inputmode="email" />
      </el-form-item>
      <el-form-item label="密碼" prop="password">
        <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
      </el-form-item>
      <el-button type="primary" size="large" native-type="submit" :loading="submitting" class="submit">登入</el-button>
    </el-form>

    <p class="switch">
      還沒有帳號？
      <RouterLink :to="{ name: 'register', query: route.query.redirect ? { redirect: route.query.redirect } : {} }">
        註冊
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

.hint {
  color: var(--color-muted);
}

.alert {
  margin-bottom: 16px;
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
