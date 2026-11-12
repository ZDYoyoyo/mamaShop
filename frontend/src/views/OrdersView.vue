<script setup>
/** 我的訂單（需要登入）：新到舊，每頁 10 筆；頁碼存在網址上（?page=2） */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listMyOrders } from '@/api/orders'
import LoadError from '@/components/LoadError.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import { formatDateTime, formatPrice } from '@/utils/format'
import { SHIPPING_METHOD_LABELS } from '@/utils/labels'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const result = ref({ items: [], totalElements: 0 })
const loading = ref(true)
const errorMessage = ref('')

const page = computed(() => {
  const n = Number(route.query.page)
  return Number.isInteger(n) && n > 0 ? n : 1
})

let latestRequest = 0
async function load(pageNo) {
  const requestId = ++latestRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await listMyOrders({ page: pageNo, size: PAGE_SIZE })
    if (requestId === latestRequest) result.value = data
  } catch (e) {
    if (requestId === latestRequest) errorMessage.value = e.message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

// 要寫在 load、latestRequest 宣告之後（immediate 會立刻執行）
watch(page, load, { immediate: true })

function goToPage(pageNo) {
  router.push({ query: pageNo > 1 ? { page: pageNo } : {} })
}
</script>

<template>
  <h1>我的訂單</h1>

  <el-skeleton v-if="loading" animated :rows="6" />
  <LoadError v-else-if="errorMessage" :message="errorMessage" @retry="load(page)" />
  <el-empty v-else-if="result.items.length === 0" description="還沒有訂單">
    <RouterLink :to="{ name: 'products' }"><el-button type="primary">去逛逛</el-button></RouterLink>
  </el-empty>

  <template v-else>
    <ul class="orders">
      <li v-for="order in result.items" :key="order.orderNo">
        <RouterLink :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }" class="order">
          <div class="top">
            <span class="date">{{ formatDateTime(order.createdAt) }}</span>
            <OrderStatusTag :status="order.status" />
          </div>
          <div class="summary">
            {{ order.firstProductName }}<span v-if="order.totalQuantity > 1" class="more"> 等 {{ order.totalQuantity }} 件</span>
          </div>
          <div class="bottom">
            <span class="meta">{{ order.orderNo }}・{{ SHIPPING_METHOD_LABELS[order.shippingMethod] }}</span>
            <strong class="total">{{ formatPrice(order.totalAmount) }}</strong>
          </div>
        </RouterLink>
      </li>
    </ul>

    <el-pagination
      class="pagination"
      layout="prev, pager, next"
      :current-page="page"
      :page-size="PAGE_SIZE"
      :total="result.totalElements"
      :pager-count="5"
      hide-on-single-page
      background
      @current-change="goToPage"
    />
  </template>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
}

.orders {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order {
  display: block;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
}

.order:hover {
  border-color: var(--el-color-primary-light-5);
}

.top,
.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.date,
.meta,
.more {
  color: var(--color-muted);
  font-size: 0.85rem;
}

.summary {
  margin: 6px 0;
  font-weight: 500;
}

.total {
  color: var(--color-primary);
}

.pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>
