<script setup>
/**
 * 後台首頁。A01 只放歡迎訊息；今日訂單數、待確認、低庫存等概覽數字在 A05 加入。
 */
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <h1>概覽</h1>
  <p>歡迎回來，{{ auth.user?.name }}</p>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin: 0 0 8px;
}
</style>
