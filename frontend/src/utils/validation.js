/**
 * 表單驗證規則，和後端的驗證一致（B06 的 RegisterRequest、@ValidPassword）。
 * 前端先檢查是為了讓使用者馬上看到錯誤；後端一定還會再檢查一次（前端可以被繞過）。
 */

/** 8～72 個英文字母、數字或符號（BCrypt 只處理 72 bytes，中文一個字佔 3 bytes） */
export const PASSWORD_PATTERN = /^[\x21-\x7E]{8,72}$/
export const PASSWORD_MESSAGE = '需為 8 到 72 個英文字母、數字或符號'

/** 台灣手機：09 開頭共 10 碼 */
export const PHONE_PATTERN = /^09\d{8}$/
export const PHONE_MESSAGE = '需為 09 開頭的 10 碼手機號碼'

/** Element Plus el-form 的規則寫法：trigger 'blur' 表示離開輸入框時檢查 */
export const rules = {
  email: [
    { required: true, message: '請輸入 Email', trigger: 'blur' },
    { type: 'email', message: 'Email 格式不正確', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '請輸入密碼', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE, trigger: 'blur' },
  ],
  name: [
    { required: true, message: '請輸入姓名', trigger: 'blur' },
    { max: 50, message: '最多 50 個字', trigger: 'blur' },
  ],
  phone: [{ pattern: PHONE_PATTERN, message: PHONE_MESSAGE, trigger: 'blur' }],
}

/**
 * 把後端回傳的欄位錯誤（ApiError.fieldErrors）轉成 { 欄位: 訊息 }，
 * 給 el-form-item 的 :error 屬性顯示在對應的輸入框下方。
 */
export function toFieldErrorMap(apiError) {
  return Object.fromEntries((apiError?.fieldErrors ?? []).map((e) => [e.field, e.message]))
}
