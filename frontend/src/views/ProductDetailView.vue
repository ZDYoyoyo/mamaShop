<script setup>
/**
 * 商品詳情：圖片、名稱、價格、庫存、描述、加入購物車。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getProduct } from '@/api/catalog'
import ProductImage from '@/components/ProductImage.vue'
import { MAX_QUANTITY_PER_PRODUCT, useCartStore } from '@/stores/cart'
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

const cart = useCartStore()
const quantity = ref(1)
const maxQuantity = computed(() => Math.min(product.value?.stock ?? 1, MAX_QUANTITY_PER_PRODUCT))

function addToCart() {
  const before = cart.items.find((item) => item.productId === product.value.id)?.quantity ?? 0
  const after = cart.addItem(product.value, quantity.value)
  if (after - before < quantity.value) {
    // 購物車裡原本就有，加上去超過庫存：只加到庫存上限
    ElMessage.warning(`庫存只剩 ${product.value.stock} 件，購物車已有 ${after} 件`)
  } else {
    ElMessage.success(`已加入購物車（共 ${after} 件）`)
  }
}

// 從一個商品直接連到另一個商品時（路由一樣、只有 id 變），元件不會重建，所以要監聽 id
watch(
  () => props.id,
  async (id) => {
    loading.value = true
    notFound.value = false
    errorMessage.value = ''
    quantity.value = 1
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

      <div v-if="product.stock > 0" class="buy">
        <el-input-number v-model="quantity" :min="1" :max="maxQuantity" aria-label="數量" />
        <el-button type="primary" size="large" @click="addToCart">加入購物車</el-button>
      </div>
      <el-button v-else size="large" disabled class="buy">已售完</el-button>

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

.buy {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 20px;
}

.buy :deep(.el-button) {
  flex: 1;
}

.description {
  white-space: pre-wrap;
  color: var(--color-text);
  margin: 0;
}
</style>
