<script setup>
/**
 * 後台新增／編輯商品（同一個元件：有 id 是編輯，沒有是新增）。
 *
 * 流程：
 * - 新增：先建立商品（預設下架）→ 有選圖片就上傳 → 回列表，確認無誤再上架
 * - 編輯：修改基本資料（PUT）→ 有換圖片就上傳 → 回列表
 * - 庫存：只有新增時填初始數量；之後要在列表用「庫存」增減（理由見 StockAdjustDialog）
 *
 * 圖片在瀏覽器先縮小、重新存成 JPG 再上傳（utils/image.js：變小、去掉 GPS 位置、HEIC 轉 JPG）。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { createProduct, getAdminProduct, updateProduct, uploadProductImage } from '@/api/admin/products'
import { getCategories } from '@/api/catalog'
import LoadError from '@/components/LoadError.vue'
import ProductImage from '@/components/ProductImage.vue'
import { compressImage } from '@/utils/image'
import { PRODUCT_STATUS } from '@/utils/labels'
import { toFieldErrorMap } from '@/utils/validation'

const props = defineProps({
  // 路由參數；新增時沒有
  id: { type: String, default: null },
})

const router = useRouter()
const isEdit = computed(() => props.id !== null)

const MAX_PRICE = 99_999_999.99 // NUMERIC(10,2)
const MAX_STOCK = 100_000

const loading = ref(true)
const loadError = ref('')
const notFound = ref(false)
const categories = ref([])
/** 編輯時：後端目前的商品（顯示庫存、狀態、目前圖片） */
const product = ref(null)

const formRef = ref()
const form = reactive({ categoryId: null, name: '', description: '', price: null, stock: 0 })
const rules = {
  categoryId: [{ required: true, message: '請選擇分類', trigger: 'change' }],
  name: [
    { required: true, whitespace: true, message: '請填寫商品名稱', trigger: 'blur' },
    { max: 100, message: '名稱最多 100 個字', trigger: 'blur' },
  ],
  description: [{ max: 5000, message: '描述最多 5000 個字', trigger: 'blur' }],
  price: [{ required: true, message: '請填寫價格', trigger: 'blur' }],
  stock: [{ required: true, message: '請填寫庫存', trigger: 'blur' }],
}
const serverErrors = ref({})
const submitError = ref('')
const submitting = ref(false)

/** 新選的圖片（已壓縮）與預覽網址 */
const newImage = ref(null)
const newImagePreview = ref(null)
const imageProcessing = ref(false)
const imageError = ref('')
const fileInput = ref()

/** 載入時的表單內容：用來判斷有沒有改過（離開頁面前提醒） */
let savedSnapshot = ''
let saved = false
const snapshot = () => JSON.stringify(form)

async function load() {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  try {
    const [categoryList, current] = await Promise.all([
      getCategories(),
      isEdit.value ? getAdminProduct(props.id) : Promise.resolve(null),
    ])
    categories.value = categoryList
    if (current) {
      product.value = current
      Object.assign(form, {
        categoryId: current.categoryId,
        name: current.name,
        description: current.description ?? '',
        price: Number(current.price),
        stock: current.stock,
      })
    }
    savedSnapshot = snapshot()
  } catch (e) {
    if (e.status === 404) notFound.value = true
    else loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function pickImage() {
  fileInput.value.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 清掉，下次選同一個檔案也會觸發 change
  if (!file) return
  imageError.value = ''
  imageProcessing.value = true
  try {
    const blob = await compressImage(file)
    setNewImage(blob)
  } catch (e) {
    imageError.value = e.message
  } finally {
    imageProcessing.value = false
  }
}

function setNewImage(blob) {
  // 預覽網址（blob:...）用完要釋放，否則會一直佔記憶體
  if (newImagePreview.value) URL.revokeObjectURL(newImagePreview.value)
  newImage.value = blob
  newImagePreview.value = blob ? URL.createObjectURL(blob) : null
}

onBeforeUnmount(() => setNewImage(null))

const imageSizeText = computed(() => (newImage.value ? `${Math.round(newImage.value.size / 1024)} KB` : ''))

async function submit() {
  if (submitting.value) return
  serverErrors.value = {}
  submitError.value = ''
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    description: form.description.trim() || null,
    price: form.price,
  }
  let savedProduct
  try {
    savedProduct = isEdit.value
      ? await updateProduct(props.id, payload)
      : await createProduct({ ...payload, stock: form.stock })
  } catch (e) {
    if (e.fieldErrors?.length) serverErrors.value = toFieldErrorMap(e)
    else submitError.value = e.message
    submitting.value = false
    return
  }

  saved = true
  if (newImage.value) {
    try {
      await uploadProductImage(savedProduct.id, newImage.value)
    } catch (e) {
      submitting.value = false
      if (isEdit.value) {
        // 資料已經存好，只是圖片沒上傳成功：留在這頁讓管理員重試
        saved = false
        savedSnapshot = snapshot()
        submitError.value = `資料已儲存，但圖片上傳失敗：${e.message}。請再按一次儲存重新上傳`
      } else {
        // 商品已經建立了（下架中）：帶到編輯頁重新上傳，避免再按一次又建立一個重複的商品
        ElMessage.error({ message: `商品已建立，但圖片上傳失敗：${e.message}。請重新選擇圖片`, duration: 8000 })
        router.replace({ name: 'admin-product-edit', params: { id: savedProduct.id } })
      }
      return
    }
  }

  ElMessage.success(isEdit.value ? '已儲存' : '商品已建立（目前是下架狀態，確認無誤後在列表上架）')
  router.push({ name: 'admin-products' })
}

// 改了還沒儲存就要離開（按返回、點選單）：先確認
onBeforeRouteLeave(async () => {
  if (saved || loading.value || (snapshot() === savedSnapshot && !newImage.value)) return true
  try {
    await ElMessageBox.confirm('修改的內容還沒有儲存，確定要離開嗎？', '尚未儲存', {
      confirmButtonText: '離開',
      cancelButtonText: '留下來',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
})
</script>

<template>
  <RouterLink :to="{ name: 'admin-products' }" class="back">← 商品列表</RouterLink>
  <h1>{{ isEdit ? '編輯商品' : '新增商品' }}</h1>

  <el-skeleton v-if="loading" animated :rows="8" />
  <el-result v-else-if="notFound" icon="warning" title="找不到這個商品">
    <template #extra>
      <RouterLink :to="{ name: 'admin-products' }"><el-button type="primary">回商品列表</el-button></RouterLink>
    </template>
  </el-result>
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />

  <!-- novalidate：關掉瀏覽器內建的表單檢查，只用 Element Plus 的驗證規則。
       數字輸入框是原生 <input type="number" step="10">，瀏覽器會認為 65、1299 這種「不是 step 倍數」的值不合法，
       直接擋下送出、手機上也沒有任何提示（A03 實測發現） -->
  <el-form
    v-else
    ref="formRef"
    novalidate
    :model="form"
    :rules="rules"
    label-position="top"
    class="form"
    @submit.prevent="submit"
  >
    <section class="card">
      <h2>圖片</h2>
      <div class="image-row">
        <div class="image-preview">
          <img v-if="newImagePreview" :src="newImagePreview" alt="新圖片預覽" />
          <ProductImage v-else :src="product?.imageUrl" :alt="form.name" />
        </div>
        <div class="image-actions">
          <!-- 用自己的按鈕觸發隱藏的 <input type="file">：比較好看，手機上也能直接開相簿或相機 -->
          <input ref="fileInput" type="file" accept="image/*" class="file-input" @change="onFileChange" />
          <el-button :loading="imageProcessing" @click="pickImage">
            {{ newImage || product?.imageUrl ? '更換圖片' : '選擇圖片' }}
          </el-button>
          <el-button v-if="newImage" link type="danger" @click="setNewImage(null)">取消更換</el-button>
          <p v-if="newImage" class="hint">已壓縮為 {{ imageSizeText }}，儲存時上傳</p>
          <p v-else class="hint">手機照片會自動縮小，並移除拍攝地點等資訊</p>
          <p v-if="imageError" class="error">{{ imageError }}</p>
        </div>
      </div>
    </section>

    <section class="card">
      <h2>基本資料</h2>
      <el-form-item label="分類" prop="categoryId" :error="serverErrors.categoryId">
        <el-select v-model="form.categoryId" placeholder="請選擇分類">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="商品名稱" prop="name" :error="serverErrors.name">
        <el-input v-model="form.name" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="價格（元）" prop="price" :error="serverErrors.price">
        <el-input-number v-model="form.price" :min="0" :max="MAX_PRICE" :precision="2" :step="10" :controls="false" class="number" />
      </el-form-item>
      <el-form-item v-if="!isEdit" label="初始庫存（件）" prop="stock" :error="serverErrors.stock">
        <el-input-number v-model="form.stock" :min="0" :max="MAX_STOCK" :step="1" step-strictly class="number" />
      </el-form-item>
      <el-form-item v-else label="庫存與狀態">
        <div class="readonly">
          {{ product.stock }} 件・{{ PRODUCT_STATUS[product.status]?.label }}
          <span class="hint">（在商品列表用「庫存」按鈕與上架開關調整）</span>
        </div>
      </el-form-item>
      <el-form-item label="商品描述（選填）" prop="description" :error="serverErrors.description">
        <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" maxlength="5000" show-word-limit />
      </el-form-item>
    </section>

    <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon class="submit-error" />
    <div class="submit-row">
      <el-button type="primary" size="large" native-type="submit" :loading="submitting" :disabled="imageProcessing">
        {{ isEdit ? '儲存' : '建立商品' }}
      </el-button>
    </div>
  </el-form>
</template>

<style scoped>
.back {
  display: inline-block;
  padding: 8px 0;
  font-size: 0.9rem;
  text-decoration: none;
}

h1 {
  font-size: 1.4rem;
  margin: 0 0 16px;
}

.form {
  max-width: 720px;
}

.card {
  padding: 16px;
  margin-bottom: 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

h2 {
  font-size: 1.05rem;
  margin: 0 0 12px;
}

.image-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.image-preview {
  width: 120px;
  flex-shrink: 0;
  font-size: 0.75rem;
}

.image-preview img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
}

.file-input {
  display: none;
}

.hint {
  color: var(--color-muted);
  font-size: 0.85rem;
  margin: 8px 0 0;
}

.error {
  color: var(--color-danger);
  font-size: 0.9rem;
  margin: 8px 0 0;
}

.number {
  width: 200px;
}

.readonly {
  line-height: 1.5;
}

.submit-error {
  margin-bottom: 12px;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 600px) {
  .image-preview {
    width: 96px;
  }

  .submit-row .el-button {
    width: 100%;
  }
}
</style>
