<script setup>
/**
 * 商品列表：分類、關鍵字、分頁。
 *
 * 篩選條件都存在網址上（/products?categoryId=2&keyword=帆布&page=2），而不是元件的變數：
 * - 重新整理、分享連結，看到的是同一頁
 * - 瀏覽器「上一頁」可以回到上一個篩選條件
 * 流程：使用者操作 → 改網址（router.push）→ 監聽網址變化 → 重新查詢
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories, searchProducts } from '@/api/catalog'
import CategoryChips from '@/components/CategoryChips.vue'
import ProductGrid from '@/components/ProductGrid.vue'
import LoadError from '@/components/LoadError.vue'
import SearchBox from '@/components/SearchBox.vue'

const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

const categories = ref([])
const result = ref({ items: [], totalElements: 0 })
const loading = ref(true)
const errorMessage = ref('')
const keywordInput = ref('')

/** 從網址讀出篩選條件；亂填的值（page=abc、categoryId=-1）一律當作沒填 */
const filters = computed(() => ({
  categoryId: positiveInt(route.query.categoryId),
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword.trim() : '',
  page: positiveInt(route.query.page) ?? 1,
}))

const title = computed(() => {
  if (filters.value.keyword) return `「${filters.value.keyword}」的搜尋結果`
  const category = categories.value.find((c) => c.id === filters.value.categoryId)
  return category ? category.name : '全部商品'
})

onMounted(async () => {
  try {
    categories.value = await getCategories()
  } catch {
    // 分類載不到不影響商品列表，只是少了分類按鈕
  }
})

/**
 * 連續快速切換分類時，舊的請求可能比新的晚回來，蓋掉正確的結果。
 * 每次查詢遞增編號，回來時不是最新的那次就丟掉。
 */
let latestRequest = 0
async function load({ categoryId, keyword, page }) {
  const requestId = ++latestRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await searchProducts({ categoryId, keyword, page, size: PAGE_SIZE })
    if (requestId === latestRequest) result.value = data
  } catch (e) {
    if (requestId === latestRequest) errorMessage.value = e.message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

// 網址的篩選條件一變就重新查詢；immediate：進入頁面時也查一次。
// 注意：要寫在 load、latestRequest 宣告之後——immediate 會在這一行立刻執行，
// 如果 latestRequest 還沒宣告就會出錯（Cannot access before initialization）
watch(
  filters,
  (value) => {
    keywordInput.value = value.keyword
    load(value)
  },
  { immediate: true },
)

/** 改篩選條件：換分類或關鍵字時回到第 1 頁 */
function updateQuery(changes, { resetPage = true } = {}) {
  const next = { ...filters.value, ...changes }
  if (resetPage) next.page = 1
  router.push({
    query: {
      categoryId: next.categoryId ?? undefined,
      keyword: next.keyword || undefined,
      page: next.page > 1 ? next.page : undefined,
    },
  })
}

function positiveInt(value) {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}
</script>

<template>
  <section class="filters">
    <SearchBox v-model="keywordInput" @search="(keyword) => updateQuery({ keyword })" />
    <CategoryChips
      :categories="categories"
      :model-value="filters.categoryId"
      @update:model-value="(categoryId) => updateQuery({ categoryId, keyword: '' })"
    />
  </section>

  <div class="heading">
    <h1>{{ title }}</h1>
    <span v-if="!loading && !errorMessage" class="count">共 {{ result.totalElements }} 件</span>
  </div>

  <LoadError v-if="errorMessage" :message="errorMessage" @retry="load(filters)" />
  <template v-else>
    <ProductGrid
      :products="result.items"
      :loading="loading"
      :skeleton-count="PAGE_SIZE"
      :empty-text="filters.keyword ? '找不到符合的商品，換個關鍵字試試' : '這個分類還沒有商品'"
    >
      <!-- 有搜尋或分類條件時，給一個回到全部商品的出口 -->
      <template v-if="filters.keyword || filters.categoryId" #empty>
        <el-button type="primary" @click="router.push({ name: 'products' })">看全部商品</el-button>
      </template>
    </ProductGrid>
    <el-pagination
      v-if="!loading"
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
</template>

<style scoped>
.filters {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 8px;
}

.heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.heading h1 {
  font-size: 1.3rem;
}

.count {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.pagination {
  justify-content: center;
  margin-top: 24px;
}
</style>
