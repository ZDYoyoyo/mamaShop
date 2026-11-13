<script setup>
/**
 * 訂單操作的確認對話框（確認、出貨、送達、完成、取消、退回共用）。
 *
 * 每個操作都先跳出來說明「按下去會發生什麼」，避免在手機上誤按（後台以「不會按錯」為優先）。
 * 送出時帶上畫面上的版本號 version：
 * - 成功 → emit('done', 新的詳情)：父元件換成新資料（含新的版本號）
 * - 409（別人剛改過訂單、或狀態已經不允許）→ emit('stale', 原因)：父元件重新讀取最新狀態
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { runOrderAction } from '@/api/admin/orders'
import { ORDER_ACTIONS } from '@/utils/adminOrderActions'
import { toFieldErrorMap } from '@/utils/validation'

const props = defineProps({
  // 操作的 key（confirm、ship…）；null 時關閉
  action: { type: String, default: null },
  orderNo: { type: String, required: true },
  version: { type: Number, required: true },
})
const emit = defineEmits(['close', 'done', 'stale'])

const definition = computed(() => (props.action ? ORDER_ACTIONS[props.action] : null))

const formRef = ref()
const form = reactive({ trackingNo: '', restock: null, note: '' })
const rules = computed(() => ({
  trackingNo: definition.value?.needsTrackingNo
    ? [{ required: true, whitespace: true, message: '請填寫物流單號', trigger: 'blur' }]
    : [],
  restock: definition.value?.needsRestock ? [{ required: true, message: '請選擇商品是否加回庫存', trigger: 'change' }] : [],
}))
const serverErrors = ref({})
const submitError = ref('')
const submitting = ref(false)

// 每次打開都清空
watch(
  () => props.action,
  (action) => {
    if (!action) return
    Object.assign(form, { trackingNo: '', restock: null, note: '' })
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

  const body = { version: props.version, note: form.note.trim() || null }
  if (definition.value.needsTrackingNo) body.trackingNo = form.trackingNo.trim()
  if (definition.value.needsRestock) body.restock = form.restock

  submitting.value = true
  try {
    const detail = await runOrderAction(props.action, props.orderNo, body)
    ElMessage.success(definition.value.success)
    emit('done', detail)
    emit('close')
  } catch (e) {
    if (e.status === 409) {
      // 版本不同（CONFLICT）或狀態已經變了（INVALID_ORDER_STATUS）：畫面上的資料過時了
      emit('stale', e.message)
      emit('close')
    } else if (e.fieldErrors?.length) {
      serverErrors.value = toFieldErrorMap(e)
    } else {
      submitError.value = e.message
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="action !== null"
    :title="definition?.label"
    width="min(460px, 92vw)"
    append-to-body
    @close="emit('close')"
  >
    <template v-if="definition">
      <p class="description">{{ definition.description }}</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" novalidate @submit.prevent="submit">
        <el-form-item v-if="definition.needsTrackingNo" label="物流單號" prop="trackingNo" :error="serverErrors.trackingNo">
          <el-input v-model="form.trackingNo" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="definition.needsRestock" label="商品要加回庫存嗎？" prop="restock" :error="serverErrors.restock">
          <!-- 沒有預設值：一定要選，避免把損壞的商品加回去賣，或忘了把好的加回去 -->
          <el-radio-group v-model="form.restock" class="restock">
            <el-radio :value="true" border>要，商品完好可以再賣</el-radio>
            <el-radio :value="false" border>不要（損壞、遺失）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="備註（選填，會記在狀態紀錄）" prop="note" :error="serverErrors.note">
          <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="例如：已電話確認" />
        </el-form-item>
        <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon />
        <button type="submit" hidden />
      </el-form>
    </template>

    <template #footer>
      <el-button @click="emit('close')">返回</el-button>
      <el-button :type="definition?.type" :loading="submitting" @click="submit">{{ definition?.label }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.description {
  margin: 0 0 16px;
  line-height: 1.6;
}

.restock {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
}

.restock :deep(.el-radio) {
  margin-right: 0;
}
</style>
