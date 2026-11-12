import { useAuthStore } from '@/stores/auth'

/**
 * 路由守衛：每次換頁「之前」檢查能不能進去（類似後端 Spring Security 的路徑權限）。
 *
 * 在路由上用 meta 標記：
 *   meta: { requiresAuth: true }   需要登入（例如結帳、我的訂單）
 *   meta: { guestOnly: true }      已登入就不用再進來（登入頁、註冊頁）
 *
 * 注意：這只是「使用者體驗」——讓沒登入的人看到登入頁，而不是一個會失敗的頁面。
 * 真正的保護在後端：就算有人繞過前端，API 沒有有效 token 一樣回 401。
 *
 * @returns true 允許進入；或回傳要改去的路由
 */
export function authGuard(to) {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { name: 'home' }
  }
  return true
}

/**
 * 登入後要跳回的網址。只接受站內路徑（以 / 開頭、不是 //）：
 * 否則有人可以寄出 /login?redirect=https://釣魚網站 的連結，顧客登入後就被帶到假網站（open redirect）。
 */
export function safeRedirect(value) {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/'
  return value
}
