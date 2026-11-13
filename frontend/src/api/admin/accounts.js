import http from '@/api/http'

/** 後台管理員帳號 API（/api/admin/admins，限管理員；只管理 ADMIN 帳號） */

export function listAdmins() {
  return http.get('/admin/admins')
}

/** { email, password, name, phone }；Email 已註冊時回 409 EMAIL_ALREADY_EXISTS */
export function createAdmin(payload) {
  return http.post('/admin/admins', payload)
}

/** 停用（false）／啟用（true）；不能停用自己（409 CANNOT_DISABLE_SELF） */
export function setAdminEnabled(id, enabled) {
  return http.patch(`/admin/admins/${id}/enabled`, { enabled })
}

/** 重設密碼：該帳號之前的登入（token）全部失效，包括自己 */
export function resetAdminPassword(id, newPassword) {
  return http.put(`/admin/admins/${id}/password`, { newPassword })
}
