import axios from 'axios'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

/**
 * 所有呼叫後端的請求都用這個 axios 實例（不要直接用 axios.get）。
 *
 * 攔截器（interceptor）就像後端的 Filter：
 * - 請求送出前：自動帶上 Authorization: Bearer {token}
 * - 收到回應後：成功時直接回傳 data；失敗時統一轉成 ApiError，401 自動登出並導向登入頁
 *
 * 用法：
 *   const categories = await http.get('/categories')   // 拿到的就是 data，不用再 .data.data
 *   try { ... } catch (e) { e.message; e.fieldErrors }  // e 是 ApiError
 */
const http = axios.create({
  // 相對路徑：開發時由 Vite proxy、正式環境由 Nginx 轉給後端
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  // 後端統一回應格式 { success: true, data: ... }，這裡直接取出 data
  (response) => response.data?.data,
  (error) => {
    const apiError = toApiError(error)
    if (apiError.status === 401 && !error.config?.skipAuthRedirect) {
      handleUnauthorized()
    }
    return Promise.reject(apiError)
  },
)

/**
 * 401：token 過期、被竄改、帳號被停用或密碼被重設。
 * 清掉登入狀態，帶著目前的網址導向登入頁，登入後可以回到原本的頁面。
 */
function handleUnauthorized() {
  const auth = useAuthStore()
  const wasLoggedIn = auth.isLoggedIn
  auth.logout()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    // 原本是登入狀態才提示：讓顧客知道為什麼突然跳到登入頁
    if (wasLoggedIn) ElMessage.warning('登入已過期，請重新登入')
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
}

/** 統一的錯誤物件，對應後端的 ApiError（code、message、fieldErrors）加上 HTTP 狀態碼 */
export class ApiError extends Error {
  constructor({ status, code, message, fieldErrors = [] }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }

  /** 某個欄位的錯誤訊息，表單用來顯示在輸入框旁邊 */
  fieldMessage(field) {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }
}

/**
 * 回應不是後端的 JSON 格式時（例如 Nginx 的錯誤頁）的訊息。
 * 502／503／504：後端沒開或正在重新啟動（部署新版本時），通常稍等一下就好。
 */
function fallbackMessage(status) {
  if (status === 502 || status === 503 || status === 504) return '伺服器暫時無法使用，請稍後再試'
  return '系統發生錯誤，請稍後再試'
}

function toApiError(error) {
  if (error.response) {
    const body = error.response.data?.error
    return new ApiError({
      status: error.response.status,
      code: body?.code ?? 'UNKNOWN',
      message: body?.message ?? fallbackMessage(error.response.status),
      fieldErrors: body?.fieldErrors ?? [],
    })
  }
  if (error.code === 'ECONNABORTED') {
    return new ApiError({ status: 0, code: 'TIMEOUT', message: '連線逾時，請稍後再試' })
  }
  return new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線到伺服器，請檢查網路' })
}

export default http
