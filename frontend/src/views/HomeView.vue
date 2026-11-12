<script setup>
/**
 * 首頁：搜尋、分類入口、最新上架商品。
 * 搜尋或點分類時導向商品列表頁（/products），篩選條件放在網址上。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategories, searchProducts } from '@/api/catalog'
import CategoryChips from '@/components/CategoryChips.vue'
import ProductGrid from '@/components/ProductGrid.vue'
import SearchBox from '@/components/SearchBox.vue'

const router = useRouter()

const categories = ref([])
const latest = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    // 兩個請求同時送出，不用等一個完成才送下一個
    const [categoryList, page] = await Promise.all([getCategories(), searchProducts({ size: 8 })])
    categories.value = categoryList
    latest.value = page.items
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
})

function search(keyword) {
  router.push({ name: 'products', query: keyword ? { keyword } : {} })
}

function selectCategory(categoryId) {
  router.push({ name: 'products', query: categoryId ? { categoryId } : {} })
}
</script>

<template>
  <section class="hero">
    <h1>包包與布料</h1>
    <p>每一件都是家裡用心挑選、製作的商品</p>
    <SearchBox class="hero-search" @search="search" />
  </section>

  <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

  <template v-else>
    <section class="block">
      <CategoryChips :categories="categories" :model-value="null" @update:model-value="selectCategory" />
    </section>

    <section class="block">
      <div class="block-header">
        <h2>最新上架</h2>
        <RouterLink :to="{ name: 'products' }">看全部商品</RouterLink>
      </div>
      <ProductGrid :products="latest" :loading="loading" :skeleton-count="8" />
    </section>
  </template>
</template>

<style scoped>
.hero {
  padding: 24px 16px;
  margin: -16px -16px 16px;
  background: var(--color-surface);
  text-align: center;
}

.hero h1 {
  margin: 0 0 4px;
  font-size: 1.6rem;
}

.hero p {
  margin: 0 0 16px;
  color: var(--color-muted);
}

.hero-search {
  max-width: 480px;
  margin: 0 auto;
}

.block {
  margin-bottom: 24px;
}

.block-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.block-header h2 {
  font-size: 1.2rem;
}
</style>
