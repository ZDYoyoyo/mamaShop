<script setup>
/**
 * 訂單詳情（需要登入；只能看自己的，別人的訂單後端回 404）：
 * 進度、商品、金額、收件資訊、物流單號；待確認時可以取消。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { cancelOrder, getOrder } from '@/api/orders'
import LoadError from '@/components/LoadError.vue'
import OrderProgress from '@/components/OrderProgress.vue'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import { formatDateTime, formatPrice } from '@/utils/format'
import { cvsBrandLabel, isCancellableByCustomer, PAYMENT_METHOD_LABELS, SHIPPING_METHOD_LABELS } from '@/utils/labels'

const props = defineProps({
  orderNo: { type: String, required: true },
})

const order = ref(null)
const loading = ref(true)
const notFound = ref(false)
const errorMessage = ref('')

const cancellable = computed(() => order.value && isCancellableByCustomer(order.value.status))

async function load() {
  loading.value = true
  notFound.value = false
  errorMessage.value = ''
  try {
    order.value = await getOrder(props.orderNo)
  } catch (e) {
    if (e.status === 404) notFound.value = true
    else errorMessage.value = e.message
  } finally {
    loading.value = false
  }
}

watch(() => props.orderNo, load, { immediate: true })

const cancelling = ref(false)
async function cancel() {
  cancelling.value = true
  try {
    order.value = await cancelOrder(props.orderNo)
    ElMessage.success('訂單已取消')
  } catch (e) {
    // 例如顧客按下取消的同時，家人剛好確認了訂單：顯示原因並重新讀取最新狀態
    ElMessage.error(e.message)
    await load()
  } finally {
    cancelling.value = false
  }
}
</script>

<template>
  <el-skeleton v-if="loading" animated :rows="6" />

  <el-result v-else-if="notFound" icon="warning" title="找不到這筆訂單">
    <template #extra>
      <RouterLink :to="{ name: 'orders' }"><el-button>回我的訂單</el-button></RouterLink>
    </template>
  </el-result>

  <LoadError v-else-if="errorMessage" :message="errorMessage" @retry="load" />

  <template v-else>
    <RouterLink :to="{ name: 'orders' }" class="back">← 我的訂單</RouterLink>
    <div class="heading">
      <h1>訂單 {{ order.orderNo }}</h1>
      <OrderStatusTag :status="order.status" />
    </div>

    <section class="card">
      <OrderProgress :order="order" />
      <p v-if="order.trackingNo" class="tracking">物流單號：{{ order.trackingNo }}</p>
    </section>

    <section class="card">
      <h2>商品</h2>
      <ul class="lines">
        <li v-for="item in order.items" :key="item.productId">
          <RouterLink :to="{ name: 'product-detail', params: { id: item.productId } }">{{ item.productName }}</RouterLink>
          <span class="qty">{{ formatPrice(item.unitPrice) }} × {{ item.quantity }}</span>
          <span>{{ formatPrice(item.subtotal) }}</span>
        </li>
      </ul>
      <div class="row"><span>商品小計</span><span>{{ formatPrice(order.itemsAmount) }}</span></div>
      <div class="row"><span>運費</span><span>{{ formatPrice(order.shippingFee) }}</span></div>
      <div class="row total"><span>總計</span><strong>{{ formatPrice(order.totalAmount) }}</strong></div>
    </section>

    <section class="card">
      <h2>收件與付款</h2>
      <p>{{ order.recipientName }}　{{ order.recipientPhone }}</p>
      <p v-if="order.shippingMethod === 'HOME_DELIVERY'">{{ SHIPPING_METHOD_LABELS.HOME_DELIVERY }}：{{ order.shippingAddress }}</p>
      <p v-else>
        {{ SHIPPING_METHOD_LABELS.CVS_PICKUP }}：{{ cvsBrandLabel(order.cvsBrand) }} {{ order.cvsStoreName }}（{{ order.cvsStoreCode }}）
      </p>
      <p>{{ PAYMENT_METHOD_LABELS[order.paymentMethod] }}{{ order.paymentStatus === 'PAID' ? '（已付款）' : '' }}</p>
      <p v-if="order.note" class="note">備註：{{ order.note }}</p>
      <p class="muted">下單時間：{{ formatDateTime(order.createdAt) }}</p>
    </section>

    <div v-if="cancellable" class="actions">
      <!-- el-popconfirm：按下後先跳出確認，避免誤按 -->
      <el-popconfirm title="確定要取消這筆訂單嗎？" confirm-button-text="取消訂單" cancel-button-text="再想想" @confirm="cancel">
        <template #reference>
          <el-button type="danger" plain :loading="cancelling">取消訂單</el-button>
        </template>
      </el-popconfirm>
      <p class="muted">賣家確認訂單後就不能自行取消，需要請聯絡賣家</p>
    </div>
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
  font-size: 1.2rem;
  margin: 8px 0;
  word-break: break-all;
}

h2 {
  font-size: 1.05rem;
  margin: 0 0 12px;
}

.card {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  margin-bottom: 16px;
}

.tracking {
  margin: 12px 0 0;
}

.lines {
  list-style: none;
  padding: 0;
  margin: 0 0 8px;
}

.lines li {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 12px;
  padding: 4px 0;
}

.qty,
.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
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

p {
  margin: 4px 0;
}

.note {
  white-space: pre-wrap;
}

.actions {
  text-align: center;
}
</style>
