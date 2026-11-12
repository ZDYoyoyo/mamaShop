<script setup>
/** 商品卡片：列表上的一個商品（圖片、名稱、價格、庫存提示），點擊進入商品詳情 */
import { computed } from 'vue'
import ProductImage from './ProductImage.vue'
import { formatPrice, stockLabel } from '@/utils/format'

const props = defineProps({
  // 後端 ProductSummaryResponse：{ id, name, price, stock, imageUrl }
  product: { type: Object, required: true },
})

const stock = computed(() => stockLabel(props.product.stock))
const soldOut = computed(() => props.product.stock <= 0)
</script>

<template>
  <RouterLink :to="{ name: 'product-detail', params: { id: product.id } }" class="card" :class="{ 'sold-out': soldOut }">
    <ProductImage :src="product.imageUrl" :alt="product.name" />
    <div class="info">
      <h3 class="name">{{ product.name }}</h3>
      <div class="meta">
        <span class="price">{{ formatPrice(product.price) }}</span>
        <el-tag v-if="stock" :type="stock.type" size="small" effect="plain" disable-transitions>{{ stock.text }}</el-tag>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  color: inherit;
  text-decoration: none;
}

.card.sold-out :deep(.product-image) {
  opacity: 0.5;
}

.info {
  padding: 8px 2px;
}

.name {
  margin: 0 0 4px;
  font-size: 0.95rem;
  font-weight: 500;
  /* 名稱太長時最多顯示兩行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.price {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
