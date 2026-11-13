<script setup>
/**
 * 後台商品列表：搜尋、分類與狀態篩選、分頁（條件都放在網址上，重新整理或按上一頁不會不見）。
 * 每一列可以直接上下架、調整庫存、進入編輯。
 *
 * 用卡片式列表而不是表格（el-table）：家人多半用手機管理，表格在手機上要左右捲動，很難用。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { Plus, Search } from '@element-plus/icons-vue'
import { changeProductStatus, listAdminProducts } from '@/api/admin/products'
import { getCategories } from '@/api/catalog'
import LoadError from '@/components/LoadError.vue'
import ProductImage from '@/components/ProductImage.vue'
import StockAdjustDialog from '@/components/admin/StockAdjustDialog.vue'
import { formatPrice, LOW_STOCK_THRESHOLD } from '@/utils/format'

const PAGE_SIZE = 20

const route = useRoute()
const router = useRouter()

const categories = ref([])
const result = ref({ items: [], totalElements: 0 })
const loading = ref(true)
const errorMessage = ref('')
const keywordInput = ref('')
/** 正在切換上下架的商品 id（切換中 switch 顯示轉圈、不能再按） */
const switching = reactive(new Set())
/** 正在調整庫存的商品（打開對話框） */
const stockTarget = ref(null)

/** 從網址讀出篩選條件；亂填的值一律當作沒填 */
const filters = computed(() => ({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword.trim() : '',
  categoryId: positiveInt(route.query.categoryId),
  status: ['ON_SALE', 'OFF_SALE'].includes(route.query.status) ? route.query.status : '',
  page: positiveInt(route.query.page) ?? 1,
}))

const hasFilters = computed(() => Boolean(filters.value.keyword || filters.value.categoryId || filters.value.status))

onMounted(async () => {
  try {
    categories.value = await getCategories()
  } catch {
    // 分類載不到只是少了分類篩選，不影響列表
  }
})

/** 舊的請求比新的晚回來時丟掉（同前台商品列表） */
let latestRequest = 0
async function load() {
  const requestId = ++latestRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await listAdminProducts({ ...filters.value, size: PAGE_SIZE })
    if (requestId === latestRequest) result.value = data
  } catch (e) {
    if (requestId === latestRequest) errorMessage.value = e.message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

// 要寫在 load、latestRequest 宣告之後（immediate 會立刻執行）
watch(
  filters,
  (value) => {
    keywordInput.value = value.keyword
    load()
  },
  { immediate: true },
)

/** 改篩選條件：寫回網址（換條件時回到第 1 頁） */
function updateQuery(changes, { resetPage = true } = {}) {
  const next = { ...filters.value, ...changes }
  if (resetPage) next.page = 1
  const query = {}
  if (next.keyword) query.keyword = next.keyword
  if (next.categoryId) query.categoryId = String(next.categoryId)
  if (next.status) query.status = next.status
  if (next.page > 1) query.page = String(next.page)
  router.push({ query })
}

function replaceItem(updated) {
  const index = result.value.items.findIndex((p) => p.id === updated.id)
  if (index >= 0) result.value.items[index] = updated
}

async function toggleStatus(product, onSale) {
  switching.add(product.id)
  try {
    const updated = await changeProductStatus(product.id, onSale ? 'ON_SALE' : 'OFF_SALE')
    replaceItem(updated)
    ElMessage.success(`「${updated.name}」已${onSale ? '上架' : '下架'}`)
  } catch (e) {
    // switch 綁的是商品的狀態，失敗時沒有更新，會自動回到原本的位置
    ElMessage.error(e.message)
  } finally {
    switching.delete(product.id)
  }
}

function stockClass(stock) {
  if (stock === 0) return 'stock-out'
  if (stock <= LOW_STOCK_THRESHOLD) return 'stock-low'
  return ''
}

function positiveInt(value) {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}
</script>

<template>
  <div class="page-header">
    <h1>商品</h1>
    <RouterLink :to="{ name: 'admin-product-new' }">
      <el-button type="primary" :icon="Plus">新增商品</el-button>
    </RouterLink>
  </div>

  <section class="filters">
    <el-input
      v-model="keywordInput"
      placeholder="搜尋商品名稱"
      clearable
      class="keyword"
      @keyup.enter="updateQuery({ keyword: keywordInput.trim() })"
      @clear="updateQuery({ keyword: '' })"
    >
      <template #append>
        <el-button :icon="Search" aria-label="搜尋" @click="updateQuery({ keyword: keywordInput.trim() })" />
      </template>
    </el-input>
    <el-select
      :model-value="filters.categoryId"
      placeholder="全部分類"
      clearable
      class="select"
      aria-label="分類"
      @update:model-value="(categoryId) => updateQuery({ categoryId })"
    >
      <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
    </el-select>
    <el-select
      :model-value="filters.status"
      placeholder="全部狀態"
      clearable
      class="select"
      aria-label="狀態"
      @update:model-value="(status) => updateQuery({ status })"
    >
      <el-option label="上架中" value="ON_SALE" />
      <el-option label="已下架" value="OFF_SALE" />
    </el-select>
  </section>

  <el-skeleton v-if="loading" animated :rows="6" />
  <LoadError v-else-if="errorMessage" :message="errorMessage" @retry="load" />
  <el-empty v-else-if="result.items.length === 0" :description="hasFilters ? '沒有符合條件的商品' : '還沒有商品'">
    <el-button v-if="hasFilters" @click="router.push({ query: {} })">清除篩選</el-button>
    <RouterLink v-else :to="{ name: 'admin-product-new' }">
      <el-button type="primary">新增第一個商品</el-button>
    </RouterLink>
  </el-empty>

  <template v-else>
    <p class="count">共 {{ result.totalElements }} 件</p>
    <ul class="products">
      <li v-for="product in result.items" :key="product.id" class="product" :class="{ off: product.status === 'OFF_SALE' }">
        <RouterLink :to="{ name: 'admin-product-edit', params: { id: product.id } }" class="thumb">
          <ProductImage :src="product.imageUrl" :alt="product.name" />
        </RouterLink>

        <div class="info">
          <RouterLink :to="{ name: 'admin-product-edit', params: { id: product.id } }" class="name">
            {{ product.name }}
          </RouterLink>
          <div class="meta">{{ product.categoryName }}・{{ formatPrice(product.price) }}</div>
          <div class="stock" :class="stockClass(product.stock)">
            庫存 {{ product.stock }} 件<template v-if="product.stock === 0">（售完）</template>
          </div>
        </div>

        <div class="actions">
          <el-switch
            :model-value="product.status === 'ON_SALE'"
            :loading="switching.has(product.id)"
            inline-prompt
            active-text="上架"
            inactive-text="下架"
            :aria-label="`${product.name} 上架`"
            @update:model-value="(onSale) => toggleStatus(product, onSale)"
          />
          <el-button @click="stockTarget = product">庫存</el-button>
          <RouterLink :to="{ name: 'admin-product-edit', params: { id: product.id } }">
            <el-button>編輯</el-button>
          </RouterLink>
        </div>
      </li>
    </ul>

    <el-pagination
      class="pagination"
      layout="prev, pager, next"
      :current-page="filters.page"
      :page-size="PAGE_SIZE"
      :total="result.totalElements"
      :pager-count="5"
      hide-on-single-page
      background
      @current-change="(page) => updateQuery({ page }, { resetPage: false })"
    />
  </template>

  <StockAdjustDialog :product="stockTarget" @close="stockTarget = null" @adjusted="replaceItem" />
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

h1 {
  font-size: 1.4rem;
  margin: 0;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.keyword {
  flex: 1 1 240px;
}

.select {
  width: 140px;
}

.count {
  color: var(--color-muted);
  margin: 0 0 8px;
}

.products {
  list-style: none;
  margin: 0;
  padding: 0;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.product {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
}

.product + .product {
  border-top: 1px solid var(--color-border);
}

.product.off .thumb,
.product.off .info {
  opacity: 0.55;
}

.thumb {
  font-size: 0.7rem;
  text-decoration: none;
}

.name {
  font-weight: 600;
  color: var(--color-text);
  text-decoration: none;
}

.meta {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.stock {
  font-size: 0.9rem;
}

.stock-low {
  color: var(--el-color-warning);
}

.stock-out {
  color: var(--color-danger);
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Element Plus 相鄰按鈕預設有左邊距，這裡已經用 gap */
.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.pagination {
  justify-content: center;
  margin-top: 16px;
}

/* 手機：操作按鈕移到下一行 */
@media (max-width: 600px) {
  .product {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .select {
    flex: 1 1 120px;
  }
}
</style>
