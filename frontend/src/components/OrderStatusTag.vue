<script setup>
/** 訂單狀態標籤：待確認（橘）、備貨中／已出貨（主題色）、已完成（綠）、已取消（灰） */
import { computed } from 'vue'
import { ORDER_STATUS } from '@/utils/labels'

const props = defineProps({
  status: { type: String, required: true },
})

const meta = computed(() => ORDER_STATUS[props.status] ?? { label: props.status, type: 'info' })
</script>

<template>
  <el-tag :type="meta.type" effect="light" disable-transitions>{{ meta.label }}</el-tag>
</template>
