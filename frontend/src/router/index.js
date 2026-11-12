import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { authGuard } from './guards'

/**
 * 路由：網址 → 頁面元件。
 *
 * createWebHistory：網址長得像 /products/1（不是 /#/products/1）。
 * 正式環境的 Nginx 要設定「找不到檔案就回傳 index.html」（D03），否則直接打開 /products/1 會 404。
 *
 * 除了首頁，其他頁面用 () => import(...) 延遲載入：使用者進到那一頁才下載程式，首頁比較快。
 * 需要登入的頁面加上 meta: { requiresAuth: true }（見 guards.js）。
 */
export const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('@/views/ProductListView.vue'),
  },
  {
    // (\\d+)：只接受數字，/products/abc 會落到 404 頁
    path: '/products/:id(\\d+)',
    name: 'product-detail',
    component: () => import('@/views/ProductDetailView.vue'),
    // 把路由參數 id 當成元件的 props 傳入，元件不用自己讀 route.params
    props: true,
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('@/views/CartView.vue'),
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('@/views/CheckoutView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/checkout/complete/:orderNo',
    name: 'checkout-complete',
    component: () => import('@/views/CheckoutCompleteView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  {
    // 其他所有網址
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // 換頁時捲到最上面；按「上一頁」時回到原本捲動的位置。同一頁只改 query（換分頁）也捲到最上面
  scrollBehavior(to, from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

// 每次換頁之前檢查登入狀態（guards.js）
router.beforeEach(authGuard)

// 每次換頁先把標題設回網站名稱，需要的頁面（例如商品詳情）再自己改
router.afterEach(() => {
  document.title = 'mamaShop'
})

export default router
