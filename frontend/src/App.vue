<script setup>
import { RouterLink, RouterView } from 'vue-router'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <!-- el-config-provider：Element Plus 元件的文字（分頁「前往」、空狀態「暫無資料」等）用繁體中文 -->
  <el-config-provider :locale="zhTw">
    <header class="site-header">
      <RouterLink to="/" class="brand">mamaShop</RouterLink>
      <nav class="nav">
        <RouterLink :to="{ name: 'products' }">商品</RouterLink>
        <span v-if="auth.isLoggedIn">{{ auth.user?.name }}</span>
        <RouterLink v-else :to="{ name: 'login' }">登入</RouterLink>
      </nav>
    </header>

    <main class="site-main">
      <!-- 依網址顯示對應的頁面（router/index.js） -->
      <RouterView />
    </main>
  </el-config-provider>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--color-background);
  border-bottom: 1px solid var(--color-border);
}

.brand {
  font-weight: 700;
  font-size: 1.2rem;
  color: var(--color-text);
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 16px;
  align-items: center;
}

.nav a {
  color: var(--color-text);
  text-decoration: none;
}

.nav a.router-link-active {
  color: var(--color-primary);
}

.site-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 16px;
}
</style>
