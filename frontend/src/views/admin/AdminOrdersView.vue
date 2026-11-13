<script setup>
/**
 * 後台訂單列表：依狀態（上方標籤）與運送方式篩選、分頁，新到舊。條件都放在網址上。
 * 顧客打電話來報訂單編號時，可以用「訂單編號」直接打開那筆訂單。
 */
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { listAdminOrders } from '@/api/admin/orders'
import LoadError from '@/components/LoadError.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import { formatDateTime, formatPrice } from '@/utils/format'
import { ORDER_STATUS, SHIPPING_METHOD_LABELS } from '@/utils/labels'

const PAGE_SIZE = 20

/** 上方的狀態標籤：依處理順序排列（待付款要接金流才會出現，P01 再加） */
const STATUS_TABS = ['', 'PENDING_CONFIRM', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURNED']

const route = useRoute()
const router = useRouter()

const result = ref({ items: [], totalElements: 0 })
const loading = ref(true)
const errorMessage = ref('')
const orderNoInput = ref('')

const filters = computed(() => ({
  status: route.query.status in ORDER_STATUS ? route.query.status : '',
  shippingMethod: route.query.shippingMethod in SHIPPING_METHOD_LABELS ? route.query.shippingMethod : '',
  page: positiveInt(route.query.page) ?? 1,
}))

let latestRequest = 0
async function load() {
  const requestId = ++latestRequest
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await listAdminOrders({ ...filters.value, size: PAGE_SIZE })
    if (requestId === latestRequest) result.value = data
  } catch (e) {
    if (requestId === latestRequest) errorMessage.value = e.message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

// 要寫在 load、latestRequest 宣告之後（immediate 會立刻執行）
watch(filters, load, { immediate: true })

function updateQuery(changes, { resetPage = true } = {}) {
  const next = { ...filters.value, ...changes }
  if (resetPage) next.page = 1
  const query = {}
  if (next.status) query.status = next.status
  if (next.shippingMethod) query.shippingMethod = next.shippingMethod
  if (next.page > 1) query.page = String(next.page)
  router.push({ query })
}

function openByOrderNo() {
  const orderNo = orderNoInput.value.trim().toUpperCase()
  if (orderNo) router.push({ name: 'admin-order-detail', params: { orderNo } })
}

function statusTabLabel(status) {
  return status ? ORDER_STATUS[status].label : '全部'
}

function positiveInt(value) {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}
</script>

<template>
  <h1>訂單</h1>

  <section class="toolbar">
    <el-input
      v-model="orderNoInput"
      placeholder="輸入訂單編號直接查詢"
      clearable
      class="lookup"
      @keyup.enter="openByOrderNo"
    >
      <template #append>
        <el-button @click="openByOrderNo">查詢</el-button>
      </template>
    </el-input>
    <el-select
      :model-value="filters.shippingMethod"
      placeholder="全部運送方式"
      clearable
      class="select"
      aria-label="運送方式"
      @update:model-value="(shippingMethod) => updateQuery({ shippingMethod })"
    >
      <el-option v-for="(label, code) in SHIPPING_METHOD_LABELS" :key="code" :label="label" :value="code" />
    </el-select>
  </section>

  <!-- 狀態標籤：手機上可以左右滑 -->
  <div class="tabs" role="tablist" aria-label="訂單狀態">
    <el-button
      v-for="status in STATUS_TABS"
      :key="status || 'all'"
      round
      :type="filters.status === status ? 'primary' : 'default'"
      role="tab"
      :aria-selected="filters.status === status"
      @click="updateQuery({ status })"
    >
      {{ statusTabLabel(status) }}
    </el-button>
  </div>

  <el-skeleton v-if="loading" animated :rows="6" />
  <LoadError v-else-if="errorMessage" :message="errorMessage" @retry="load" />
  <el-empty
    v-else-if="result.items.length === 0"
    :description="filters.status ? `沒有「${statusTabLabel(filters.status)}」的訂單` : '還沒有訂單'"
  />

  <template v-else>
    <p class="count">共 {{ result.totalElements }} 筆</p>
    <ul class="orders">
      <li v-for="order in result.items" :key="order.orderNo">
        <RouterLink :to="{ name: 'admin-order-detail', params: { orderNo: order.orderNo } }" class="order">
          <div class="line">
            <span class="time">{{ formatDateTime(order.createdAt) }}</span>
            <OrderStatusTag :status="order.status" />
          </div>
          <div class="line">
            <span class="who">
              {{ order.recipientName }}
              <span v-if="order.customerName !== order.recipientName" class="muted">（{{ order.customerName }} 訂）</span>
            </span>
            <strong class="total">{{ formatPrice(order.totalAmount) }}</strong>
          </div>
          <div class="line muted">
            <span>{{ order.orderNo }}</span>
            <span>{{ SHIPPING_METHOD_LABELS[order.shippingMethod] }}・{{ order.totalQuantity }} 件</span>
          </div>
        </RouterLink>
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
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin: 0 0 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.lookup {
  flex: 1 1 260px;
  max-width: 420px;
}

.select {
  width: 160px;
}

.tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  margin-bottom: 12px;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tabs :deep(.el-button) {
  flex-shrink: 0;
}

.tabs :deep(.el-button + .el-button) {
  margin-left: 0;
}

.count {
  color: var(--color-muted);
  margin: 0 0 8px;
}

.orders {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order {
  display: block;
  padding: 12px 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text);
  text-decoration: none;
}

.order:hover {
  border-color: var(--color-primary);
}

.line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.line + .line {
  margin-top: 4px;
}

.time {
  font-weight: 600;
}

.total {
  color: var(--color-primary);
}

.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.pagination {
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 600px) {
  .select {
    flex: 1 1 140px;
  }
}
</style>
