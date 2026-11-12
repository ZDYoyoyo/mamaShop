<script setup>
import { onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { ArrowDown, ShoppingCart } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()
const route = useRoute()
const router = useRouter()

// 打開網站時確認 token 還有效（可能已過期，或帳號被停用、密碼被重設）
onMounted(() => auth.loadProfile())

function logout() {
  auth.logout()
  ElMessage.success('已登出')
  // 正在看需要登入的頁面（例如我的訂單）就回首頁
  if (route.meta.requiresAuth) router.push({ name: 'home' })
}
</script>

<template>
  <!-- el-config-provider：Element Plus 元件的文字（分頁「前往」、空狀態「暫無資料」等）用繁體中文 -->
  <el-config-provider :locale="zhTw">
    <header class="site-header">
      <RouterLink to="/" class="brand">mamaShop</RouterLink>
      <nav class="nav">
        <RouterLink :to="{ name: 'products' }">商品</RouterLink>
        <!-- 已登入：名字＋下拉選單；未登入：登入連結 -->
        <el-dropdown v-if="auth.isLoggedIn" trigger="click">
          <span class="user-menu">
            {{ auth.user?.name }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push({ name: 'orders' })">我的訂單</el-dropdown-item>
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

.user-menu {
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  color: var(--color-text);
}

.cart-link {
  display: flex;
  align-items: center;
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
