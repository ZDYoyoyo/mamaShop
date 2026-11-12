<script setup>
/**
 * 最外層：依路由選擇版面。
 * - 前台頁面：StoreLayout（header＋內容）
 * - 後台（/admin）：AdminLayout 本身就是 /admin 路由的元件（router/index.js），這裡直接顯示
 */
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import StoreLayout from '@/layouts/StoreLayout.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

// 打開網站時確認 token 還有效（可能已過期，或帳號被停用、密碼被重設），並更新會員資料（含角色）
onMounted(() => auth.loadProfile())

// 第一次換頁完成前 route.matched 是空的：先不顯示任何版面，避免打開 /admin 時閃一下前台 header
const ready = computed(() => route.matched.length > 0)
const isAdmin = computed(() => route.matched.some((record) => record.meta.layout === 'admin'))
</script>

<template>
  <!-- el-config-provider：Element Plus 元件的文字（分頁「前往」、空狀態「暫無資料」等）用繁體中文 -->
  <el-config-provider :locale="zhTw">
    <template v-if="ready">
      <RouterView v-if="isAdmin" />
      <StoreLayout v-else />
    </template>
  </el-config-provider>
</template>
