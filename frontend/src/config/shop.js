/**
 * 商店的基本資料：隱私權政策頁（views/PrivacyView.vue）會用到。
 *
 * ⚠️ 目前是「暫定值」。正式上線前（D08）請改成真實的資料，改完把 placeholder 改成 false：
 * placeholder 是 true 的時候，隱私權政策頁最上面會顯示「暫定版本」的提醒，避免忘記改就上線。
 */
export const SHOP = {
  placeholder: true,
  /** 商店名稱（網站上顯示的） */
  name: 'mamaShop',
  /** 負責人或商號名稱：蒐集個資的是誰 */
  operator: 'mamaShop（負責人資料待補）',
  /** 顧客詢問、要求查詢或刪除個資時的聯絡方式 */
  contactEmail: 'service@example.com',
  /** 這份政策最後修改的日期 */
  privacyUpdatedAt: '2026-11-16',
}
