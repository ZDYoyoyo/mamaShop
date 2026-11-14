import http from './http'
import { submitPostForm } from '@/utils/postForm'

/** 物流 API（P02，綠界超商電子地圖） */

/** 物流設定：{ cvsMapEnabled }（不用登入） */
export function getLogisticsOptions() {
  return http.get('/logistics/options')
}

/**
 * 前往電子地圖的表單 { action, fields }。
 * @param brand SEVEN／FAMILY／HILIFE／OK
 * @param collection 取貨付款（貨到付款 true；線上付款 false）
 * @param mobile 手機版地圖
 */
export function getCvsMapForm({ brand, collection, mobile }) {
  return http.get('/logistics/cvs-map', { params: { brand, collection, mobile } })
}

/** 整頁跳到綠界電子地圖；選好門市後會回到 /checkout?cvsBrand=...&cvsStoreCode=... */
export function redirectToCvsMap(form) {
  submitPostForm(form)
}
