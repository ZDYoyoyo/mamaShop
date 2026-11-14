/**
 * 整個頁面跳到外部網站：組一個隱藏的 form、POST 出去。
 * 綠界的付款頁（P01）、超商電子地圖（P02）都只接受 form POST，不能用 Axios（Axios 不會換頁）。
 * 欄位原封不動送出（付款的欄位有檢查碼，改了綠界會拒絕）。
 * 正式環境的 CSP 要允許 form-action 到這些網站（deploy/nginx/security-headers.conf）
 */
export function submitPostForm({ action, fields }, { target } = {}) {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = action
  // target: '_blank'：開新分頁（例如後台列印寄件單，P03），目前的頁面不動
  if (target) form.target = target
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    form.appendChild(input)
  }
  document.body.appendChild(form)
  form.submit()
}
