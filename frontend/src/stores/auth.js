import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const TOKEN_KEY = 'mamashop.token'
const USER_KEY = 'mamashop.user'

/**
 * 登入狀態（Pinia store）。
 *
 * - token、會員資料存在 localStorage：重新整理頁面、開新分頁都還是登入狀態
 * - 任何元件都能用 useAuthStore() 讀取，例如 header 顯示名字、後台入口只給管理員看
 *
 * 為什麼用 localStorage：後端是用 Authorization header 傳 token（B06），不是 cookie。
 * 風險是 XSS（網頁被注入惡意程式碼就能讀到 token），所以：Vue 預設會跳脫所有輸出、
 * 專案不使用 v-html、正式環境再加 CSP（D03）。詳見 docs/NOTES.md。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(read(TOKEN_KEY))
  const user = ref(parseJson(read(USER_KEY)))

  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 登入成功後呼叫：傳入 /api/auth/login 的回應 */
  function setSession({ accessToken, user: profile }) {
    token.value = accessToken
    user.value = profile
    write(TOKEN_KEY, accessToken)
    write(USER_KEY, JSON.stringify(profile))
  }

  /** 登出，或 token 失效（後端回 401）時呼叫 */
  function logout() {
    token.value = null
    user.value = null
    remove(TOKEN_KEY)
    remove(USER_KEY)
  }

  return { token, user, isLoggedIn, isAdmin, setSession, logout }
})

// localStorage 在無痕模式或被瀏覽器封鎖時可能丟出例外，包起來避免整個網站壞掉
function read(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 存不了就只在這次瀏覽期間有效
  }
}

function remove(key) {
  try {
    localStorage.removeItem(key)
  } catch {
    // 忽略
  }
}

function parseJson(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
