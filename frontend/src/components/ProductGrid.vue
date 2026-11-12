<script setup>
/**
 * 商品格狀列表：載入中顯示骨架、沒有商品顯示空狀態。
 * 手機一排 2 個、平板 3 個、電腦 4 個（CSS grid，依螢幕寬度自動調整）。
 */
import ProductCard from './ProductCard.vue'

defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  // 載入中顯示幾個骨架卡片
  skeletonCount: { type: Number, default: 8 },
  emptyText: { type: String, default: '目前沒有商品' },
})
</script>

<template>
  <div v-if="loading" class="grid">
    <el-skeleton v-for="n in skeletonCount" :key="n" animated>
      <template #template>
        <el-skeleton-item variant="image" class="skeleton-image" />
        <el-skeleton-item variant="text" style="width: 80%; margin-top: 8px" />
        <el-skeleton-item variant="text" style="width: 40%" />
      </template>
    </el-skeleton>
  </div>
  <el-empty v-else-if="products.length === 0" :description="emptyText">
    <!-- 頁面可以放按鈕（例如「看全部商品」），讓顧客知道下一步 -->
    <slot name="empty" />
  </el-empty>
  <div v-else class="grid">
    <ProductCard v-for="product in products" :key="product.id" :product="product" />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 12px;
}

@media (min-width: 640px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 960px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 24px 20px;
  }
}

.skeleton-image {
  width: 100%;
  height: auto;
  aspect-ratio: 1 / 1;
}
</style>
