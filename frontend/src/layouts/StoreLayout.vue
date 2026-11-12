<script setup>
/**
 * 前台版面：上方 header（商品、會員選單、購物車）＋頁面內容。
 * 後台（/admin）用另一個版面 AdminLayout，由 App.vue 依路由選擇。
 */
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { ArrowDown, ShoppingCart } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()
const route = useRoute()
const router = useRouter()

function logout() {
  auth.logout()
  ElMessage.success('已登出')
  // 正在看需要登入的頁面（例如我的訂單）就回首頁
  if (route.meta.requiresAuth) router.push({ name: 'home' })
}
</script>

<template>
  <header class="site-header">
    <RouterLink to="/" class="brand">mamaShop</RouterLink>
    <nav class="nav">
      <RouterLink :to="{ name: 'products' }">商品</RouterLink>
      <!-- 已登入：名字＋下拉選單；未登入：登入連結 -->
      <el-dropdown v-if="auth.isLoggedIn" trigger="click">
        <span class="user-menu">
          <span class="user-name">{{ auth.user?.name }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push({ name: 'orders' })">我的訂單</el-dropdown-item>
            <!-- 管理員才看得到後台入口（真正的權限檢查在路由守衛與後端） -->
            <el-dropdown-item v-if="auth.isAdmin" @click="router.push({ name: 'admin-home' })">管理後台</el-dropdown-item>
            <el-dropdown-item divided @click="logout">登出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <RouterLink v-else :to="{ name: 'login' }">登入</RouterLink>
      <!-- 購物車圖示：右上角的數字是總件數（Pinia store 改變時自動更新） -->
      <RouterLink :to="{ name: 'cart' }" class="cart-link" :aria-label="`購物車，共 ${cart.totalQuantity} 件`">
        <el-badge :value="cart.totalQuantity" :hidden="cart.totalQuantity === 0" :max="99">
          <el-icon :size="22"><ShoppingCart /></el-icon>
        </el-badge>
      </RouterLink>
    </nav>
  </header>

  <main class="site-main">
    <!-- 依網址顯示對應的頁面（router/index.js） -->
    <RouterView />
  </main>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 右邊內距較小：最右邊的購物車圖示自己有內距 */
  padding: 4px 8px 4px 16px;
  background: var(--color-background);
  border-bottom: 1px solid var(--color-border);
}

.brand {
  flex-shrink: 0;
  font-weight: 700;
  font-size: 1.2rem;
  color: var(--color-text);
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 4px;
  align-items: center;
  /* 名字很長時讓 nav 可以縮，名字顯示「…」 */
  min-width: 0;
}

/*
 * 導覽列的連結、名字、購物車都至少 44px 高、左右有內距：
 * 手機上用手指點，可點範圍要比文字本身大（Apple 建議至少 44px）。
 */
.nav a,
.user-menu {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 8px;
  color: var(--color-text);
  text-decoration: none;
  white-space: nowrap;
}

/* 只有名字可以縮，其他連結維持原本寬度 */
.nav > a {
  flex-shrink: 0;
}

.user-menu {
  gap: 2px;
  cursor: pointer;
  min-width: 0;
}

.user-name {
  max-width: 7em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* el-dropdown 外層也要能縮 */
.nav :deep(.el-dropdown) {
  min-width: 0;
}

.nav a.cart-link {
  /* 徽章（數字）在圖示右上角，右邊多留一點空間 */
  padding: 0 12px 0 8px;
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
