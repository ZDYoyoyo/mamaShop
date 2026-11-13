<script setup>
/**
 * 後台運費設定：每種運送方式一張卡片，可改名稱、運費、啟用、排序，各自儲存。
 *
 * 種類（宅配、超商取貨）是程式定義的：結帳時依種類決定要填地址還是門市，
 * 所以不能新增或刪除，不賣某種就「停用」（至少要留一種啟用中，後端會檢查）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { listAdminShippingMethods, updateShippingMethod } from '@/api/admin/shipping'
import LoadError from '@/components/LoadError.vue'
import { formatPrice } from '@/utils/format'
import { SHIPPING_METHOD_LABELS } from '@/utils/labels'

const MAX_FEE = 99_999_999.99

/**
 * 每張卡片：saved（後端目前的值）＋ form（正在編輯的值）。
 * 兩者不同才能按儲存；儲存失敗時 form 保留，方便修正後再存。
 */
const cards = ref([])
const loading = ref(true)
const loadError = ref('')

function toForm(method) {
  return { name: method.name, fee: Number(method.fee), enabled: method.enabled, sortOrder: method.sortOrder }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const methods = await listAdminShippingMethods()
    cards.value = methods.map((method) => ({ saved: method, form: toForm(method), saving: false, error: '' }))
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function isDirty(card) {
  return JSON.stringify(card.form) !== JSON.stringify(toForm(card.saved))
}

function invalidReason(card) {
  if (!card.form.name.trim()) return '請填寫名稱'
  if (card.form.fee === null || card.form.fee === undefined) return '請填寫運費'
  return ''
}

const enabledCount = computed(() => cards.value.filter((c) => c.saved.enabled).length)

async function save(card) {
  card.error = invalidReason(card)
  if (card.error || card.saving) return
  card.saving = true
  try {
    const updated = await updateShippingMethod(card.saved.id, { ...card.form, name: card.form.name.trim() })
    card.saved = updated
    card.form = toForm(updated)
    ElMessage.success(`「${updated.name}」已儲存`)
  } catch (e) {
    card.error = e.message
  } finally {
    card.saving = false
  }
}

function reset(card) {
  card.form = toForm(card.saved)
  card.error = ''
}
</script>

<template>
  <h1>運費設定</h1>
  <p class="hint">
    修改運費只影響之後的新訂單，已成立的訂單維持下單時的運費。停用的運送方式不會出現在結帳頁，至少要保留一種啟用中。
  </p>

  <el-skeleton v-if="loading" animated :rows="6" />
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />

  <div v-else class="cards">
    <section v-for="card in cards" :key="card.saved.id" class="card" :class="{ disabled: !card.saved.enabled }">
      <header class="card-header">
        <h2>{{ SHIPPING_METHOD_LABELS[card.saved.code] ?? card.saved.code }}</h2>
        <el-tag :type="card.saved.enabled ? 'success' : 'info'" disable-transitions>
          {{ card.saved.enabled ? `啟用中・${formatPrice(card.saved.fee)}` : '已停用' }}
        </el-tag>
      </header>

      <!-- novalidate：關掉瀏覽器內建的表單檢查，只用 Element Plus 的驗證規則。
           數字輸入框是原生 <input type="number" step="10">，瀏覽器會認為 65、1299 這種「不是 step 倍數」的值不合法，
           直接擋下送出、手機上也沒有任何提示（A03 實測發現） -->
      <el-form label-position="top" novalidate @submit.prevent="save(card)">
        <el-form-item label="顯示名稱（結帳頁看到的）">
          <el-input v-model="card.form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <div class="row">
          <el-form-item label="運費（元）">
            <el-input-number v-model="card.form.fee" :min="0" :max="MAX_FEE" :precision="0" :step="10" class="number" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="card.form.sortOrder" :min="0" :max="9999" step-strictly class="number" />
          </el-form-item>
        </div>
        <el-form-item label="啟用">
          <el-switch
            v-model="card.form.enabled"
            :disabled="card.saved.enabled && enabledCount <= 1"
            inline-prompt
            active-text="啟用"
            inactive-text="停用"
            aria-label="啟用"
          />
          <span v-if="card.saved.enabled && enabledCount <= 1" class="switch-hint">唯一啟用中的方式，不能停用</span>
        </el-form-item>

        <el-alert v-if="card.error" :title="card.error" type="error" :closable="false" show-icon class="error" />
        <div class="actions">
          <el-button v-if="isDirty(card)" @click="reset(card)">還原</el-button>
          <el-button type="primary" native-type="submit" :loading="card.saving" :disabled="!isDirty(card)">儲存</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
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

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
  gap: 16px;
  max-width: 760px;
}

.card {
  padding: 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

h2 {
  font-size: 1.1rem;
  margin: 0;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0 16px;
}

.number {
  width: 150px;
}

.switch-hint {
  margin-left: 8px;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.error {
  margin-bottom: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
