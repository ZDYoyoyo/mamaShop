<script setup>
/**
 * 後台管理員帳號：列表、新增、停用／啟用、重設密碼（B14 的 API）。
 *
 * - 沒有刪除：狀態紀錄要記得「是誰操作的」，帳號只能停用
 * - 自己的帳號標示「你」，不能停用自己（後端也會擋）
 * - 重設自己的密碼後，目前的登入會失效 → 直接登出並帶到登入頁
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { Plus } from '@element-plus/icons-vue'
import { listAdmins, setAdminEnabled } from '@/api/admin/accounts'
import LoadError from '@/components/LoadError.vue'
import CreateAdminDialog from '@/components/admin/CreateAdminDialog.vue'
import ResetPasswordDialog from '@/components/admin/ResetPasswordDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()

const accounts = ref([])
const loading = ref(true)
const loadError = ref('')
const togglingId = ref(null)
const createOpen = ref(false)
/** 正在重設密碼的帳號（打開對話框） */
const resetTarget = ref(null)

const isSelf = (account) => account?.id === auth.user?.id

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    accounts.value = await listAdmins()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function replace(updated) {
  const index = accounts.value.findIndex((a) => a.id === updated.id)
  if (index >= 0) accounts.value[index] = updated
}

function onCreated(created) {
  accounts.value.push(created)
}

async function toggleEnabled(account) {
  togglingId.value = account.id
  try {
    const updated = await setAdminEnabled(account.id, !account.enabled)
    replace(updated)
    ElMessage.success(updated.enabled ? `已啟用「${updated.name}」` : `已停用「${updated.name}」，對方已經無法登入`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    togglingId.value = null
  }
}

function onPasswordReset(updated) {
  if (isSelf(updated)) {
    // 目前的 token 已經失效（發行時間早於密碼修改時間）：登出，請使用者用新密碼登入
    auth.logout()
    ElMessage.success('密碼已重設，請用新密碼重新登入')
    router.push({ name: 'login', query: { redirect: '/admin/accounts' } })
    return
  }
  replace(updated)
  ElMessage.success(`已重設「${updated.name}」的密碼，請告訴對方新密碼`)
}
</script>

<template>
  <div class="page-header">
    <h1>管理員</h1>
    <el-button type="primary" :icon="Plus" @click="createOpen = true">新增管理員</el-button>
  </div>
  <p class="hint">管理員可以管理商品、訂單與其他管理員。不再使用的帳號請停用（不能刪除，訂單的狀態紀錄要保留是誰操作的）。</p>

  <el-skeleton v-if="loading" animated :rows="4" />
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />

  <ul v-else class="list">
    <li v-for="account in accounts" :key="account.id" class="row" :class="{ disabled: !account.enabled }">
      <div class="info">
        <div class="name-line">
          <span class="name">{{ account.name }}</span>
          <el-tag v-if="isSelf(account)" size="small" disable-transitions>你</el-tag>
          <el-tag :type="account.enabled ? 'success' : 'info'" size="small" disable-transitions>
            {{ account.enabled ? '啟用中' : '已停用' }}
          </el-tag>
        </div>
        <div class="muted">{{ account.email }}<template v-if="account.phone">・{{ account.phone }}</template></div>
        <div class="muted small">
          建立於 {{ formatDateTime(account.createdAt) }}
          <template v-if="account.passwordChangedAt">・密碼重設於 {{ formatDateTime(account.passwordChangedAt) }}</template>
        </div>
      </div>

      <div class="actions">
        <el-button @click="resetTarget = account">重設密碼</el-button>
        <template v-if="!isSelf(account)">
          <el-popconfirm
            v-if="account.enabled"
            :title="`確定要停用「${account.name}」嗎？對方會立刻無法登入。`"
            confirm-button-text="停用"
            cancel-button-text="不要"
            confirm-button-type="danger"
            width="260"
            @confirm="toggleEnabled(account)"
          >
            <template #reference>
              <el-button type="danger" plain :loading="togglingId === account.id">停用</el-button>
            </template>
          </el-popconfirm>
          <el-button v-else :loading="togglingId === account.id" @click="toggleEnabled(account)">啟用</el-button>
        </template>
      </div>
    </li>
  </ul>

  <CreateAdminDialog :open="createOpen" @close="createOpen = false" @created="onCreated" />
  <ResetPasswordDialog
    :account="resetTarget"
    :is-self="isSelf(resetTarget)"
    @close="resetTarget = null"
    @reset="onPasswordReset"
  />
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h1 {
  font-size: 1.4rem;
  margin: 0;
}

.hint {
  color: var(--color-muted);
  font-size: 0.9rem;
  margin: 8px 0 16px;
  max-width: 720px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 760px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
}

.row + .row {
  border-top: 1px solid var(--color-border);
}

.row.disabled .info {
  opacity: 0.6;
}

.info {
  min-width: 0;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.name {
  font-weight: 600;
}

.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.small {
  font-size: 0.8rem;
}

.actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
