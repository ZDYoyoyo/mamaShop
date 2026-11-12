<script setup>
/**
 * 庫存調整對話框：選「進貨」或「減少」＋數量，送出「增減量」給後端。
 *
 * 為什麼不讓管理員直接輸入「庫存改成 N 件」：
 * 管理員看到「剩 10 件」到按下送出的這段時間，顧客可能剛好下單扣掉 2 件。
 * 直接存 15 會把顧客扣掉的 2 件「蓋回來」（lost update，超賣）；
 * 送「+5」由後端在資料庫裡做 stock = stock + 5，就不會有這個問題（B07）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { adjustProductStock } from '@/api/admin/products'

const props = defineProps({
  // 要調整的商品；null 時關閉
  product: { type: Object, default: null },
})
const emit = defineEmits(['close', 'adjusted'])

const MAX_AMOUNT = 100_000

const direction = ref('in')
const amount = ref(1)
const submitting = ref(false)
const errorMessage = ref('')

// 每次打開都重設
watch(
  () => props.product,
  (product) => {
    if (!product) return
    direction.value = 'in'
    amount.value = 1
    errorMessage.value = ''
  },
)

const delta = computed(() => (direction.value === 'in' ? 1 : -1) * (amount.value ?? 0))
const after = computed(() => (props.product?.stock ?? 0) + delta.value)
const invalid = computed(() => !amount.value || after.value < 0)

async function submit() {
  if (invalid.value || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const updated = await adjustProductStock(props.product.id, delta.value)
    // 以後端回傳的為準：這段時間如果有人下單，實際數字會和預覽不同
    ElMessage.success(`「${updated.name}」庫存已更新為 ${updated.stock} 件`)
    emit('adjusted', updated)
    emit('close')
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="product !== null"
    title="調整庫存"
    width="min(420px, 92vw)"
    append-to-body
    @close="emit('close')"
  >
    <template v-if="product">
      <p class="name">{{ product.name }}</p>
      <p>目前庫存 <strong>{{ product.stock }}</strong> 件</p>

      <el-radio-group v-model="direction" class="direction">
        <el-radio-button value="in">進貨（增加）</el-radio-button>
        <el-radio-button value="out">減少</el-radio-button>
      </el-radio-group>

      <div class="amount">
        <span>數量</span>
        <el-input-number v-model="amount" :min="1" :max="MAX_AMOUNT" :step="1" step-strictly aria-label="數量" />
        <span>件</span>
      </div>

      <p class="after" :class="{ negative: after < 0 }">
        調整後：{{ after }} 件<template v-if="after < 0">（庫存不夠減）</template>
      </p>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    </template>

    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="invalid" @click="submit">確定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
p {
  margin: 0 0 8px;
}

.name {
  font-weight: 600;
}

.direction {
  margin: 8px 0 16px;
}

.amount {
  display: flex;
  align-items: center;
  gap: 8px;
}

.after {
  margin-top: 16px;
  font-size: 1.05rem;
}

.after.negative {
  color: var(--color-danger);
}
</style>
