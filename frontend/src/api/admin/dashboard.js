import http from '@/api/http'

/** 後台首頁概覽（GET /api/admin/dashboard）：各種訂單數量與低庫存商品 */
export function getDashboard() {
  return http.get('/admin/dashboard')
}
