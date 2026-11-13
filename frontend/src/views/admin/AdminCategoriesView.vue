<script setup>
/**
 * 後台分類管理：列表（依排序）、新增、修改、刪除。
 * 新增與修改共用同一個對話框（editing 有 id 是修改）。
 * 前台的分類按鈕順序就是這裡的排序（數字小的在前面）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { Plus } from '@element-plus/icons-vue'
import { createCategory, deleteCategory, listAdminCategories, updateCategory } from '@/api/admin/categories'
import LoadError from '@/components/LoadError.vue'
import { toFieldErrorMap } from '@/utils/validation'

const categories = ref([])
const loading = ref(true)
const loadError = ref('')
const deletingId = ref(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    categories.value = await listAdminCategories()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ===== 新增／修改對話框 =====
const dialogOpen = ref(false)
/** 正在修改的分類；新增時是 null */
const editing = ref(null)
const formRef = ref()
const form = reactive({ name: '', sortOrder: 0 })
const rules = {
  name: [
    { required: true, whitespace: true, message: '請填寫分類名稱', trigger: 'blur' },
    { max: 50, message: '名稱最多 50 個字', trigger: 'blur' },
  ],
}
const serverErrors = ref({})
const submitError = ref('')
const submitting = ref(false)

function openCreate() {
  editing.value = null
  // 預設排在最後面
  const maxOrder = Math.max(0, ...categories.value.map((c) => c.sortOrder))
  Object.assign(form, { name: '', sortOrder: categories.value.length ? maxOrder + 1 : 0 })
  openDialog()
}

function openEdit(category) {
  editing.value = category
  Object.assign(form, { name: category.name, sortOrder: category.sortOrder })
  openDialog()
}

function openDialog() {
  serverErrors.value = {}
  submitError.value = ''
  dialogOpen.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  serverErrors.value = {}
  submitError.value = ''
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  const payload = { name: form.name.trim(), sortOrder: form.sortOrder ?? 0 }
  try {
    if (editing.value) {
      await updateCategory(editing.value.id, payload)
      ElMessage.success('分類已修改')
    } else {
      await createCategory(payload)
      ElMessage.success('分類已新增')
    }
    dialogOpen.value = false
    // 重新讀取：排序可能變了
    await load()
  } catch (e) {
    if (e.fieldErrors?.length) serverErrors.value = toFieldErrorMap(e)
    // 名稱重複（409）顯示在名稱欄位下方
    else if (e.code === 'CATEGORY_NAME_EXISTS') serverErrors.value = { name: e.message }
    else submitError.value = e.message
  } finally {
    submitting.value = false
  }
}

async function remove(category) {
  deletingId.value = category.id
  try {
    await deleteCategory(category.id)
    ElMessage.success(`已刪除「${category.name}」`)
    categories.value = categories.value.filter((c) => c.id !== category.id)
  } catch (e) {
    // 底下還有商品：後端說明原因
    ElMessage.error({ message: e.message, duration: 5000 })
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <div class="page-header">
    <h1>分類</h1>
    <el-button type="primary" :icon="Plus" @click="openCreate">新增分類</el-button>
  </div>
  <p class="hint">前台的分類按鈕依「排序」由小到大排列。底下還有商品（包含下架的）的分類不能刪除。</p>

  <el-skeleton v-if="loading" animated :rows="4" />
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />
  <el-empty v-else-if="categories.length === 0" description="還沒有分類，新增商品前要先建立分類" />

  <ul v-else class="list">
    <li v-for="category in categories" :key="category.id" class="row">
      <div class="info">
        <span class="name">{{ category.name }}</span>
        <span class="order">排序 {{ category.sortOrder }}</span>
      </div>
      <div class="actions">
        <el-button @click="openEdit(category)">修改</el-button>
        <el-popconfirm
          :title="`確定要刪除「${category.name}」嗎？`"
          confirm-button-text="刪除"
          cancel-button-text="不要"
          confirm-button-type="danger"
          width="240"
          @confirm="remove(category)"
        >
          <template #reference>
            <el-button type="danger" plain :loading="deletingId === category.id">刪除</el-button>
          </template>
        </el-popconfirm>
      </div>
    </li>
  </ul>

  <el-dialog v-model="dialogOpen" :title="editing ? '修改分類' : '新增分類'" width="min(420px, 92vw)" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" novalidate @submit.prevent="submit">
      <el-form-item label="分類名稱" prop="name" :error="serverErrors.name">
        <el-input v-model="form.name" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="排序（數字小的排前面）" prop="sortOrder" :error="serverErrors.sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="1" step-strictly />
      </el-form-item>
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon />
      <!-- 讓手機鍵盤的「完成／前往」也能送出 -->
      <button type="submit" hidden />
    </el-form>
    <template #footer>
      <el-button @click="dialogOpen = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">儲存</el-button>
    </template>
  </el-dialog>
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
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 640px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
}

.row + .row {
  border-top: 1px solid var(--color-border);
}

.info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.order {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
