<script setup>
/**
 * 商品詳情：圖片、名稱、價格、庫存、描述。
 * 「加入購物車」在 F03 加入。
 */
import { computed, ref, watch } from 'vue'
import { getProduct } from '@/api/catalog'
import ProductImage from '@/components/ProductImage.vue'
import { formatPrice, stockLabel } from '@/utils/format'

const props = defineProps({
  // 路由參數 /products/:id，router 設定 props: true 會自動傳進來
  id: { type: String, required: true },
})

const product = ref(null)
const loading = ref(true)
const notFound = ref(false)
const errorMessage = ref('')

const stock = computed(() => (product.value ? stockLabel(product.value.stock) : null))

// 從一個商品直接連到另一個商品時（路由一樣、只有 id 變），元件不會重建，所以要監聽 id
watch(
  () => props.id,
  async (id) => {
    loading.value = true
    notFound.value = false
    errorMessage.value = ''
    try {
      product.value = await getProduct(id)
      document.title = `${product.value.name} | mamaShop`
    } catch (e) {
      if (e.status === 404) notFound.value = true
      else errorMessage.value = e.message
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-skeleton v-if="loading" animated :rows="5" />

  <el-result v-else-if="notFound" icon="warning" title="找不到這個商品" sub-title="商品可能已經下架">
    <template #extra>
      <RouterLink :to="{ name: 'products' }">
        <el-button type="primary">看其他商品</el-button>
      </RouterLink>
    </template>
  </el-result>

  <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

  <article v-else class="detail">
    <ProductImage :src="product.imageUrl" :alt="product.name" class="image" />

    <div class="info">
      <RouterLink :to="{ name: 'products', query: { categoryId: product.categoryId } }" class="category">
        {{ product.categoryName }}
      </RouterLink>
      <h1>{{ product.name }}</h1>
      <div class="price-row">
        <span class="price">{{ formatPrice(product.price) }}</span>
        <el-tag v-if="stock" :type="stock.type" effect="plain" disable-transitions>{{ stock.text }}</el-tag>
      </div>

      <!--
        描述用 white-space: pre-wrap 保留換行，而不是 v-html：
        v-html 會把內容當 HTML 執行，萬一描述裡有 <script> 就是 XSS 漏洞
      -->
      <p v-if="product.description" class="description">{{ product.description }}</p>
    </div>
  </article>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 16px;
}

@media (min-width: 768px) {
  .detail {
    grid-template-columns: 1fr 1fr;
    gap: 32px;
    align-items: start;
  }
}

.category {
  font-size: 0.9rem;
  text-decoration: none;
}

h1 {
  margin: 4px 0 8px;
  font-size: 1.4rem;
}

.price-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.price {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-primary);
}

.description {
  white-space: pre-wrap;
  color: var(--color-text);
  margin: 0;
}
</style>
