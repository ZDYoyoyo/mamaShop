<script setup>
import { useRoute } from 'vue-router'

// F01 只放一個頁面讓 401 有地方導向；登入表單在 F04 實作
const route = useRoute()
</script>

<template>
  <section>
    <h1>登入</h1>
    <p>登入功能將在 F04 完成。</p>
    <p v-if="route.query.redirect" class="hint">登入後會回到：{{ route.query.redirect }}</p>
  </section>
</template>

<style scoped>
.hint {
  color: var(--color-muted);
}
</style>
