<script setup>
/**
 * 購物車頁：列出品項、修改數量、刪除、商品小計。
 * 進入頁面時向後端更新每個商品的最新資料（價格、庫存、是否下架），有變動就提示。
 */
import { onMounted, ref } from 'vue'
import ProductImage from '@/components/ProductImage.vue'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'

const cart = useCartStore()
const refreshing = ref(false)
const notices = ref([])

onMounted(async () => {
  if (cart.items.length === 0) return
  refreshing.value = true
  try {
    notices.value = await cart.refresh()
  } finally {
    refreshing.value = false
  }
})

function lineTotal(item) {
  return (Math.round(item.price * 100) * item.quantity) / 100
}
</script>

<template>
  <h1>購物車</h1>

  <el-empty v-if="cart.items.length === 0" description="購物車是空的">
    <RouterLink :to="{ name: 'products' }">
      <el-button type="primary">去逛逛</el-button>
    </RouterLink>
  </el-empty>

  <template v-else>
    <el-alert v-if="notices.length" type="warning" :closable="false" show-icon class="notices">
      <template #title>
        <div v-for="notice in notices" :key="notice">{{ notice }}</div>
      </template>
    </el-alert>

    <ul v-loading="refreshing" class="items">
      <li v-for="item in cart.items" :key="item.productId" class="item" :class="{ inactive: item.status !== 'ok' }">
        <RouterLink :to="{ name: 'product-detail', params: { id: item.productId } }" class="thumb">
          <ProductImage :src="item.imageUrl" :alt="item.name" />
        </RouterLink>

        <div class="detail">
          <RouterLink :to="{ name: 'product-detail', params: { id: item.productId } }" class="name">
            {{ item.name }}
          </RouterLink>
          <div class="unit-price">{{ formatPrice(item.price) }}</div>

          <div class="controls">
            <el-tag v-if="item.status === 'sold_out'" type="info" disable-transitions>已售完</el-tag>
            <el-tag v-else-if="item.status === 'unavailable'" type="info" disable-transitions>已下架</el-tag>
            <el-input-number
              v-else
              :model-value="item.quantity"
              :min="1"
              :max="Math.min(item.stock, 99)"
              size="small"
              :aria-label="`${item.name} 數量`"
              @update:model-value="(value) => cart.updateQuantity(item.productId, value)"
            />
            <el-button link type="danger" @click="cart.removeItem(item.productId)">刪除</el-button>
          </div>
        </div>

        <div class="line-total">{{ item.status === 'ok' ? formatPrice(lineTotal(item)) : '' }}</div>
      </li>
    </ul>

    <section class="summary">
      <div class="row">
        <span>商品小計</span>
        <strong class="subtotal">{{ formatPrice(cart.subtotal) }}</strong>
      </div>
      <p class="hint">運費於結帳時依運送方式計算</p>
      <p v-if="cart.hasProblems" class="hint warning">已售完或下架的商品不會結帳，可以先刪除</p>
      <!-- 結帳頁在 F05 完成後啟用 -->
      <el-button type="primary" size="large" class="checkout" disabled>前往結帳</el-button>
    </section>
  </template>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
}

.notices {
  margin-bottom: 12px;
}

.items {
  list-style: none;
  padding: 0;
  margin: 0;
}

.item {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}

.thumb {
  text-decoration: none;
  font-size: 0.75rem;
}

.item.inactive .thumb,
.item.inactive .name,
.item.inactive .unit-price {
  opacity: 0.5;
}

.name {
  color: var(--color-text);
  text-decoration: none;
  font-weight: 500;
}

.unit-price {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

.line-total {
  font-weight: 600;
  white-space: nowrap;
}

.summary {
  margin-top: 16px;
  padding: 16px;
  background: var(--color-surface);
  border-radius: 8px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.subtotal {
  font-size: 1.3rem;
  color: var(--color-primary);
}

.hint {
  margin: 4px 0;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.hint.warning {
  color: var(--el-color-warning);
}

.checkout {
  width: 100%;
  margin-top: 12px;
}
</style>
