import http from './http'

/** 會員相關 API（對應後端 B06） */

/** 註冊顧客帳號，成功回傳會員資料（不含 token，之後要再登入） */
export function register({ email, password, name, phone }) {
  return http.post('/auth/register', { email, password, name, phone: phone || null })
}

/**
 * 登入，回傳 { accessToken, tokenType, expiresIn, user }。
 * skipAuthRedirect：密碼錯誤時後端回 401，這時不要觸發「token 失效 → 導向登入頁」的處理
 */
export function login({ email, password }) {
  return http.post('/auth/login', { email, password }, { skipAuthRedirect: true })
}

/** 目前登入者的資料 */
export function getMe({ skipAuthRedirect = false } = {}) {
  return http.get('/users/me', { skipAuthRedirect })
}
