package com.mamashop.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 綠界金流設定（P01），對應 application.yml 的 app.payment.ecpay。
 *
 * <p>商店代號、HashKey、HashIV 一律從環境變數讀，不寫在程式或設定檔裡：
 * HashKey／HashIV 外洩的話，任何人都能偽造「付款成功」的通知。
 * 本機開發用綠界公開的測試商店（.env.example），正式環境填自己申請的。
 *
 * @param enabled    沒開的話結帳頁不顯示線上付款
 * @param cashierUrl 綠界付款頁的網址：測試環境 payment-stage.ecpay.com.tw、正式環境 payment.ecpay.com.tw
 * @param siteUrl    網站網址，組出付款通知（ReturnURL）與付款後返回（ClientBackURL）的網址
 */
@ConfigurationProperties(prefix = "app.payment.ecpay")
public record EcpayProperties(boolean enabled, String merchantId, String hashKey, String hashIv,
                              String cashierUrl, String siteUrl) {

    public EcpayProperties {
        // 開了卻沒設定完整：啟動就失敗，不要等到顧客付款時才發現
        if (enabled && (isBlank(merchantId) || isBlank(hashKey) || isBlank(hashIv)
                || isBlank(cashierUrl) || isBlank(siteUrl))) {
            throw new IllegalStateException(
                    "已開啟線上付款（ECPAY_ENABLED=true），但 ECPAY_MERCHANT_ID、ECPAY_HASH_KEY、ECPAY_HASH_IV、"
                            + "ECPAY_CASHIER_URL、SITE_URL 沒有設定完整");
        }
        siteUrl = siteUrl != null && siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
