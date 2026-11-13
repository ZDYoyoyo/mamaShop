<script setup>
/**
 * 後台首頁概覽：家人打開後台第一眼要知道「現在有什麼事要處理」。
 * - 每個數字都可以點，直接打開篩選好的訂單列表
 * - 待確認、待出貨、未取件有數量時用顏色提醒
 * - 低庫存商品可以點進去編輯（進貨要在商品列表用「庫存」調整）
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getDashboard } from '@/api/admin/dashboard'
import LoadError from '@/components/LoadError.vue'
import { formatDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const data = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const updatedAt = ref(null)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    data.value = await getDashboard()
    updatedAt.value = new Date()
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 數字卡片：todo 表示「有數量就代表有事要處理」，用顏色提醒 */
const tiles = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { key: 'pending', label: '待確認', value: d.pendingConfirm, hint: '聯絡顧客、確認訂單', todo: true, query: { status: 'PENDING_CONFIRM' } },
    { key: 'shipment', label: '待出貨', value: d.awaitingShipment, hint: '已確認、等待出貨', todo: true, query: { status: 'PROCESSING' } },
    {
      key: 'pickup',
      label: '超商未取件',
      value: d.awaitingPickup,
      hint: '已到門市、顧客還沒取',
      todo: true,
      query: { status: 'DELIVERED', shippingMethod: 'CVS_PICKUP' },
    },
    { key: 'today', label: '今日訂單', value: d.todayOrders, hint: '今天新進的訂單', todo: false, query: {} },
    {
      key: 'returned',
      label: '最近退回',
      value: d.returnedRecently,
      hint: `最近 ${d.returnedDays} 天`,
      todo: false,
      query: { status: 'RETURNED' },
    },
  ]
})
</script>

<template>
  <div class="page-header">
    <div>
      <h1>概覽</h1>
      <p class="muted">歡迎回來，{{ auth.user?.name }}</p>
    </div>
    <el-button :icon="Refresh" :loading="loading" @click="load">重新整理</el-button>
  </div>

  <el-skeleton v-if="loading && !data" animated :rows="6" />
  <LoadError v-else-if="errorMessage && !data" :message="errorMessage" @retry="load" />

  <template v-else-if="data">
    <el-alert v-if="errorMessage" :title="`更新失敗：${errorMessage}`" type="error" show-icon class="stale" />

    <section class="tiles">
      <RouterLink
        v-for="tile in tiles"
        :key="tile.key"
        :to="{ name: 'admin-orders', query: tile.query }"
        class="tile"
        :class="{ todo: tile.todo && tile.value > 0 }"
      >
        <span class="label">{{ tile.label }}</span>
        <strong class="value">{{ tile.value }}</strong>
        <span class="hint">{{ tile.hint }}</span>
      </RouterLink>
    </section>

    <section class="card">
      <div class="card-header">
        <h2>低庫存商品</h2>
        <span class="muted">上架中、剩 {{ data.lowStockThreshold }} 件以下，共 {{ data.lowStockCount }} 件</span>
      </div>
      <p v-if="data.lowStockProducts.length === 0" class="muted">庫存都很充足</p>
      <ul v-else class="low-stock">
        <li v-for="product in data.lowStockProducts" :key="product.id">
          <RouterLink :to="{ name: 'admin-product-edit', params: { id: product.id } }">{{ product.name }}</RouterLink>
          <el-tag v-if="product.stock === 0" type="danger" disable-transitions>售完</el-tag>
          <span v-else class="stock-low">剩 {{ product.stock }} 件</span>
        </li>
      </ul>
      <p v-if="data.lowStockCount > data.lowStockProducts.length" class="muted">
        只列出最少的 {{ data.lowStockProducts.length }} 件，
        <RouterLink :to="{ name: 'admin-products' }">到商品列表看全部</RouterLink>
      </p>
    </section>

    <p v-if="updatedAt" class="muted updated">更新時間 {{ formatDateTime(updatedAt) }}</p>
  </template>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

h1 {
  font-size: 1.4rem;
  margin: 0;
}

h2 {
  font-size: 1.05rem;
  margin: 0;
}

.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
  margin: 4px 0 0;
}

.stale {
  margin-bottom: 12px;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text);
  text-decoration: none;
}

.tile:hover {
  border-color: var(--color-primary);
}

/* 有事要處理：橘色邊框與數字 */
.tile.todo {
  border-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.tile.todo .value {
  color: var(--el-color-warning-dark-2);
}

.label {
  font-weight: 600;
}

.value {
  font-size: 2rem;
  line-height: 1.2;
}

.hint {
  color: var(--color-muted);
  font-size: 0.8rem;
}

.card {
  padding: 16px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.card-header .muted {
  margin: 0;
}

.low-stock {
  list-style: none;
  margin: 0;
  padding: 0;
}

.low-stock li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
}

.low-stock li + li {
  border-top: 1px solid var(--color-border);
}

.low-stock a {
  overflow-wrap: anywhere;
}

.stock-low {
  color: var(--el-color-warning);
  white-space: nowrap;
}

.updated {
  margin-top: 12px;
}

@media (max-width: 480px) {
  .tiles {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
