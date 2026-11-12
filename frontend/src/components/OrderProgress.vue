<script setup>
/**
 * 訂單進度：下單 → 確認 → 出貨 → 送達 → 完成，每一步顯示發生的時間。
 * 已取消、已退回的訂單不顯示進度，改顯示一行說明。
 * 手機（窄螢幕）直向排列，電腦橫向排列。
 */
import { computed, onMounted, ref } from 'vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  // 後端 OrderDetailResponse
  order: { type: Object, required: true },
})

/** 每個狀態「已完成幾步」：el-steps 的 active */
const FINISHED_STEPS = {
  PENDING_PAYMENT: 1,
  PENDING_CONFIRM: 1,
  PROCESSING: 2,
  SHIPPED: 3,
  DELIVERED: 4,
  COMPLETED: 5,
}

const steps = computed(() => [
  { title: '已下單', time: props.order.createdAt },
  { title: '已確認', time: props.order.confirmedAt },
  { title: '已出貨', time: props.order.shippedAt },
  { title: '已送達', time: props.order.deliveredAt },
  { title: '已完成', time: props.order.completedAt },
])

const ended = computed(() => {
  if (props.order.status === 'CANCELLED') {
    return { title: '訂單已取消', time: props.order.cancelledAt, type: 'info' }
  }
  if (props.order.status === 'RETURNED') {
    return { title: '包裹已退回', time: props.order.returnedAt, type: 'warning' }
  }
  return null
})

const direction = ref('vertical')
onMounted(() => {
  direction.value = window.matchMedia?.('(min-width: 640px)').matches ? 'horizontal' : 'vertical'
})
</script>

<template>
  <el-alert
    v-if="ended"
    :title="ended.title"
    :description="ended.time ? formatDateTime(ended.time) : ''"
    :type="ended.type"
    :closable="false"
    show-icon
  />
  <el-steps
    v-else
    :active="FINISHED_STEPS[order.status] ?? 0"
    :direction="direction"
    finish-status="success"
    align-center
    class="progress"
  >
    <el-step
      v-for="step in steps"
      :key="step.title"
      :title="step.title"
      :description="step.time ? formatDateTime(step.time) : ''"
    />
  </el-steps>
</template>

<style scoped>
/* 直向時每一步留一樣的高度：還沒發生的步驟沒有時間，不加高度會擠在一起 */
.progress.el-steps--vertical :deep(.el-step) {
  min-height: 52px;
}
</style>
