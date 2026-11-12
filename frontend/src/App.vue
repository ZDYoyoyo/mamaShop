<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <header class="site-header">
    <RouterLink to="/" class="brand">mamaShop</RouterLink>
    <nav>
      <span v-if="auth.isLoggedIn">{{ auth.user?.name }}</span>
      <RouterLink v-else to="/login">登入</RouterLink>
    </nav>
  </header>

  <main class="site-main">
    <!-- 依網址顯示對應的頁面（router/index.js） -->
    <RouterView />
  </main>
</template>

<style scoped>
.site-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.brand {
  font-weight: 700;
  font-size: 1.2rem;
  color: var(--color-text);
  text-decoration: none;
}

.site-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 16px;
}
</style>
