import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

/**
 * 路由：網址 → 頁面元件。
 *
 * createWebHistory：網址長得像 /products/1（不是 /#/products/1）。
 * 正式環境的 Nginx 要設定「找不到檔案就回傳 index.html」（D03），否則直接打開 /products/1 會 404。
 *
 * 除了首頁，其他頁面用 () => import(...) 延遲載入：使用者進到那一頁才下載程式，首頁比較快。
 * 前台（F02～F06）、後台（A01～）的頁面之後陸續加在這裡；需要登入的路由守衛在 F04 加入。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      // 其他所有網址
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

export default router
