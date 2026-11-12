<script setup>
/**
 * 訂單完成頁。向後端查詢訂單（而不是沿用結帳頁的資料）：
 * 重新整理、從書籤打開都看得到，而且顯示的是後端實際成立的金額。
 */
import { onMounted, ref } from 'vue'
import { getOrder } from '@/api/orders'
import { formatPrice } from '@/utils/format'
import { cvsBrandLabel, PAYMENT_METHOD_LABELS } from '@/utils/labels'

const props = defineProps({
  orderNo: { type: String, required: true },
})

const order = ref(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    order.value = await getOrder(props.orderNo)
  } catch (e) {
    errorMessage.value = e.status === 404 ? '找不到這筆訂單' : e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <el-skeleton v-if="loading" animated :rows="5" />
  <el-result v-else-if="errorMessage" icon="warning" :title="errorMessage" />

  <template v-else>
    <el-result icon="success" title="訂單已送出" :sub-title="`訂單編號 ${order.orderNo}`">
      <template #extra>
        <p class="next">我們確認訂單後會盡快出貨，{{ PAYMENT_METHOD_LABELS[order.paymentMethod] }}，收到商品時付款。</p>
      </template>
    </el-result>

    <section class="card">
      <h2>訂單內容</h2>
      <ul class="lines">
        <li v-for="item in order.items" :key="item.productId">
          <span>{{ item.productName }} × {{ item.quantity }}</span>
          <span>{{ formatPrice(item.subtotal) }}</span>
        </li>
      </ul>
      <div class="row"><span>商品小計</span><span>{{ formatPrice(order.itemsAmount) }}</span></div>
      <div class="row"><span>運費</span><span>{{ formatPrice(order.shippingFee) }}</span></div>
      <div class="row total"><span>總計</span><strong>{{ formatPrice(order.totalAmount) }}</strong></div>
    </section>

    <section class="card">
      <h2>收件資訊</h2>
      <p>{{ order.recipientName }}　{{ order.recipientPhone }}</p>
      <p v-if="order.shippingMethod === 'HOME_DELIVERY'">宅配：{{ order.shippingAddress }}</p>
      <p v-else>超商取貨：{{ cvsBrandLabel(order.cvsBrand) }} {{ order.cvsStoreName }}（{{ order.cvsStoreCode }}）</p>
      <p v-if="order.note" class="note">備註：{{ order.note }}</p>
    </section>

    <div class="actions">
      <RouterLink :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }">
        <el-button type="primary">查看訂單</el-button>
      </RouterLink>
      <RouterLink :to="{ name: 'products' }"><el-button>繼續購物</el-button></RouterLink>
    </div>
  </template>
</template>

<style scoped>
.next {
  color: var(--color-muted);
  margin: 0;
}

.card {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  margin-bottom: 16px;
}

h2 {
  font-size: 1.05rem;
  margin: 0 0 12px;
}

.lines {
  list-style: none;
  padding: 0;
  margin: 0 0 8px;
}

.lines li,
.row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
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
  color: var(--color-muted);
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
