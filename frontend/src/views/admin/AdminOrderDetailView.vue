<script setup>
/**
 * 後台訂單詳情：進度、操作按鈕、商品與金額、收件資訊、下單會員、狀態紀錄。
 *
 * 操作（確認、出貨…）都經過 OrderActionDialog，送出時帶上畫面上的版本號 detail.version。
 * 家人可能同時在不同手機上處理同一筆訂單：後端發現版本不同會回 409，
 * 這時重新讀取，讓畫面變成最新狀態，再由家人決定要不要繼續操作（B10 的樂觀鎖）。
 */
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getAdminOrder } from '@/api/admin/orders'
import LoadError from '@/components/LoadError.vue'
import OrderProgress from '@/components/OrderProgress.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import OrderActionDialog from '@/components/admin/OrderActionDialog.vue'
import { availableActions, ORDER_ACTIONS } from '@/utils/adminOrderActions'
import { formatDateTime, formatPrice } from '@/utils/format'
import {
  cvsBrandLabel,
  ORDER_STATUS,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  ROLE_LABELS,
  SHIPPING_METHOD_LABELS,
} from '@/utils/labels'

const props = defineProps({
  orderNo: { type: String, required: true },
})

/** 後端的 AdminOrderDetailResponse：{ order, customer, histories, version } */
const detail = ref(null)
const loading = ref(true)
const notFound = ref(false)
const loadError = ref('')
/** 正在進行的操作（打開對話框） */
const currentAction = ref(null)

const order = computed(() => detail.value?.order)
const actions = computed(() => (order.value ? availableActions(order.value.status) : []))

/** 回列表：從列表點進來就回到原本的篩選與頁碼（vue-router 在 history.state.back 記錄上一頁） */
const backTo = computed(() => {
  const back = window.history.state?.back
  return typeof back === 'string' && /^\/admin\/orders(\?|$)/.test(back) ? back : { name: 'admin-orders' }
})

async function load() {
  loading.value = true
  notFound.value = false
  loadError.value = ''
  try {
    detail.value = await getAdminOrder(props.orderNo)
  } catch (e) {
    if (e.status === 404) notFound.value = true
    else loadError.value = e.message
  } finally {
    loading.value = false
  }
}

watch(() => props.orderNo, load, { immediate: true })

function onDone(updated) {
  detail.value = updated
}

/** 409：別人剛改過，或狀態已經不允許。說明原因並重新讀取 */
async function onStale(message) {
  ElMessage.warning({ message: `${message}（已重新讀取最新狀態）`, duration: 5000 })
  await load()
}

function historyTitle(history) {
  const to = ORDER_STATUS[history.toStatus]?.label ?? history.toStatus
  if (!history.fromStatus) return `建立訂單（${to}）`
  return `${ORDER_STATUS[history.fromStatus]?.label ?? history.fromStatus} → ${to}`
}

function operatorText(history) {
  const role = ROLE_LABELS[history.operatorRole]
  return role ? `${history.operatorName}（${role}）` : history.operatorName
}
</script>

<template>
  <RouterLink :to="backTo" class="back">← 訂單列表</RouterLink>

  <el-skeleton v-if="loading && !detail" animated :rows="8" />
  <el-result v-else-if="notFound" icon="warning" title="找不到這筆訂單" :sub-title="`訂單編號：${orderNo}`">
    <template #extra>
      <RouterLink :to="{ name: 'admin-orders' }"><el-button type="primary">回訂單列表</el-button></RouterLink>
    </template>
  </el-result>
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />

  <template v-else-if="detail">
    <div class="heading">
      <h1>訂單 {{ order.orderNo }}</h1>
      <OrderStatusTag :status="order.status" />
      <el-tag :type="order.paymentStatus === 'PAID' ? 'success' : 'info'" effect="plain" disable-transitions>
        {{ PAYMENT_STATUS_LABELS[order.paymentStatus] ?? order.paymentStatus }}
      </el-tag>
    </div>
    <p class="muted">下單時間 {{ formatDateTime(order.createdAt) }}</p>
    <!-- 線上付款（P01）：取消之後才付款成功（逾時取消和付款剛好同時）、或重複付款，錢要退給顧客 -->
    <el-alert
      v-if="order.paymentMethod === 'ONLINE' && order.paymentStatus === 'PAID' && ['CANCELLED', 'RETURNED'].includes(order.status)"
      type="error"
      :closable="false"
      show-icon
      title="這筆訂單已經收到線上付款，但訂單不會出貨：請到綠界廠商後台退款"
      description="綠界交易編號在下方「狀態紀錄」的備註裡"
      class="refund-alert"
    />

    <!-- 操作：依目前狀態顯示；已完成、已取消、已退回沒有操作 -->
    <section v-if="actions.length" class="actions">
      <el-button
        v-for="key in actions"
        :key="key"
        :type="ORDER_ACTIONS[key].type"
        :plain="ORDER_ACTIONS[key].type === 'danger'"
        size="large"
        @click="currentAction = key"
      >
        {{ ORDER_ACTIONS[key].label }}
      </el-button>
    </section>

    <section class="card">
      <OrderProgress :order="order" />
      <p v-if="order.trackingNo" class="tracking">物流單號：{{ order.trackingNo }}</p>
    </section>

    <div class="columns">
      <section class="card">
        <h2>商品</h2>
        <ul class="lines">
          <li v-for="item in order.items" :key="item.productId">
            <RouterLink :to="{ name: 'admin-product-edit', params: { id: item.productId } }">{{ item.productName }}</RouterLink>
            <span class="muted">{{ formatPrice(item.unitPrice) }} × {{ item.quantity }}</span>
            <span>{{ formatPrice(item.subtotal) }}</span>
          </li>
        </ul>
        <div class="row"><span>商品小計</span><span>{{ formatPrice(order.itemsAmount) }}</span></div>
        <div class="row"><span>運費</span><span>{{ formatPrice(order.shippingFee) }}</span></div>
        <div class="row total"><span>總計</span><strong>{{ formatPrice(order.totalAmount) }}</strong></div>
        <p class="muted">{{ PAYMENT_METHOD_LABELS[order.paymentMethod] }}</p>
      </section>

      <section class="card">
        <h2>收件資訊</h2>
        <p>{{ order.recipientName }}　<a :href="`tel:${order.recipientPhone}`">{{ order.recipientPhone }}</a></p>
        <p v-if="order.shippingMethod === 'HOME_DELIVERY'">宅配：{{ order.shippingAddress }}</p>
        <p v-else>
          {{ SHIPPING_METHOD_LABELS[order.shippingMethod] }}：{{ cvsBrandLabel(order.cvsBrand) }}
          {{ order.cvsStoreName }}（店號 {{ order.cvsStoreCode }}）
        </p>
        <p v-if="order.note" class="note">顧客備註：{{ order.note }}</p>

        <h2 class="sub">下單會員</h2>
        <p>{{ detail.customer.name }}</p>
        <p>
          <a :href="`tel:${detail.customer.phone}`">{{ detail.customer.phone }}</a>・<a :href="`mailto:${detail.customer.email}`">{{ detail.customer.email }}</a>
        </p>
      </section>
    </div>

    <section class="card">
      <h2>狀態紀錄</h2>
      <el-timeline class="timeline">
        <el-timeline-item
          v-for="(history, index) in detail.histories"
          :key="index"
          :timestamp="formatDateTime(history.createdAt)"
          placement="top"
        >
          <div class="history-title">{{ historyTitle(history) }}</div>
          <div class="muted">{{ operatorText(history) }}</div>
          <div v-if="history.note" class="note">{{ history.note }}</div>
        </el-timeline-item>
      </el-timeline>
    </section>

    <OrderActionDialog
      :action="currentAction"
      :order-no="order.orderNo"
      :version="detail.version"
      @close="currentAction = null"
      @done="onDone"
      @stale="onStale"
    />
  </template>
</template>

<style scoped>
.back {
  display: inline-block;
  padding: 8px 0;
  font-size: 0.9rem;
  text-decoration: none;
}

.heading {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

h1 {
  font-size: 1.3rem;
  margin: 4px 0;
  word-break: break-all;
}

h2 {
  font-size: 1.05rem;
  margin: 0 0 12px;
}

h2.sub {
  margin-top: 16px;
}

p {
  margin: 4px 0;
}

.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 16px;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.card {
  padding: 16px;
  margin-bottom: 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
  gap: 0 16px;
}

.tracking {
  margin-top: 12px;
}

.lines {
  list-style: none;
  padding: 0;
  margin: 0 0 8px;
}

.lines li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  padding: 4px 0;
}

.row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
}

.row.total {
  margin-top: 8px;
  font-size: 1.1rem;
}

.row.total strong {
  color: var(--color-primary);
}

.note {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.timeline {
  padding-left: 4px;
}

.history-title {
  font-weight: 600;
}

@media (max-width: 600px) {
  .actions :deep(.el-button) {
    flex: 1 1 40%;
  }
}
.refund-alert {
  margin: 8px 0;
}
</style>
