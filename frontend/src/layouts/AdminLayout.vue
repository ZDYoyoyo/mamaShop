<script setup>
/**
 * 後台版面：左側選單＋上方列＋頁面內容。
 *
 * - 電腦：選單固定在左邊
 * - 手機（< 768px）：選單收起來，按左上角「☰」從左邊滑出（el-drawer）
 *   兩種都畫出來，用 CSS 決定顯示哪一個，不需要用 JavaScript 判斷螢幕寬度
 *
 * 選單項目在 ADMIN_MENU：每個後台功能一行。
 */
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { CollectionTag, DataLine, Expand, Goods, Tickets, User, Van } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

/** 後台選單：name 是路由名稱 */
const ADMIN_MENU = [
  { name: 'admin-home', title: '概覽', icon: DataLine },
  { name: 'admin-orders', title: '訂單', icon: Tickets },
  { name: 'admin-products', title: '商品', icon: Goods },
  { name: 'admin-categories', title: '分類', icon: CollectionTag },
  { name: 'admin-shipping', title: '運費設定', icon: Van },
  { name: 'admin-accounts', title: '管理員', icon: User },
]

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const drawerOpen = ref(false)

// 子頁面（例如編輯商品）用 meta.menu 指定要亮哪一項
const activeMenu = computed(() => route.meta.menu ?? route.name)

function go(name) {
  drawerOpen.value = false
  router.push({ name })
}

function logout() {
  auth.logout()
  ElMessage.success('已登出')
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin">
    <aside class="sidebar">
      <div class="sidebar-title">mamaShop 後台</div>
      <el-menu :default-active="activeMenu" class="menu" @select="go">
        <el-menu-item v-for="item in ADMIN_MENU" :key="item.name" :index="item.name">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 手機：從左邊滑出的選單 -->
    <el-drawer v-model="drawerOpen" direction="ltr" size="240px" title="mamaShop 後台" class="admin-drawer">
      <el-menu :default-active="activeMenu" class="menu" @select="go">
        <el-menu-item v-for="item in ADMIN_MENU" :key="item.name" :index="item.name">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <div class="body">
      <header class="topbar">
        <button type="button" class="menu-button" aria-label="開啟選單" @click="drawerOpen = true">
          <el-icon :size="22"><Expand /></el-icon>
        </button>
        <span class="topbar-title">mamaShop 後台</span>
        <div class="topbar-actions">
          <span class="admin-name">{{ auth.user?.name }}</span>
          <RouterLink :to="{ name: 'home' }">回前台</RouterLink>
          <el-button link @click="logout">登出</el-button>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: #f5f6f8;
}

.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--color-background);
  border-right: 1px solid var(--color-border);
}

.sidebar-title {
  padding: 16px 20px;
  font-weight: 700;
}

.menu {
  border-right: none;
}

.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 52px;
  padding: 0 16px;
  background: var(--color-background);
  border-bottom: 1px solid var(--color-border);
}

.menu-button,
.topbar-title {
  display: none;
}

.menu-button {
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-left: -10px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text);
  cursor: pointer;
}

.topbar-title {
  font-weight: 700;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.topbar-actions a {
  color: var(--color-text);
  text-decoration: none;
}

.admin-name {
  color: var(--color-muted);
}

.content {
  padding: 20px;
  max-width: 1200px;
  width: 100%;
}

@media (max-width: 767px) {
  .sidebar,
  .admin-name {
    display: none;
  }

  .menu-button {
    display: flex;
  }

  .topbar-title {
    display: inline;
  }

  .topbar-actions {
    gap: 12px;
  }

  .content {
    padding: 16px;
  }
}
</style>

<!-- el-drawer 會被移到 <body> 底下，scoped 樣式套不到，所以用一般樣式（class 名稱加前綴避免影響別處） -->
<style>
.admin-drawer .el-drawer__header {
  margin-bottom: 0;
}

.admin-drawer .el-drawer__body {
  padding: 8px 0;
}
</style>
