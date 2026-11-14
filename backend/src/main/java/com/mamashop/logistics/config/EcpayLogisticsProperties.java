package com.mamashop.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 綠界物流設定（P02），對應 application.yml 的 app.logistics.ecpay。
 *
 * <p>電子地圖只需要商店代號（不用 HashKey），P03 建立物流單時才需要密鑰。
 *
 * @param enabled    沒開的話結帳頁維持手動填門市
 * @param merchantId 物流的商店代號（和金流可以是同一個綠界帳號）
 * @param c2c        true：店到店（C2C，個人／小商店常用，寄件到門市）；false：大宗寄倉（B2C，要先把貨送到物流中心）
 * @param mapUrl     電子地圖網址：測試環境 logistics-stage.ecpay.com.tw、正式環境 logistics.ecpay.com.tw
 * @param siteUrl    網站網址，組出「選好門市後回來」的網址
 */
@ConfigurationProperties(prefix = "app.logistics.ecpay")
public record EcpayLogisticsProperties(boolean enabled, String merchantId, boolean c2c, String mapUrl, String siteUrl) {

    public EcpayLogisticsProperties {
        if (enabled && (isBlank(merchantId) || isBlank(mapUrl) || isBlank(siteUrl))) {
            throw new IllegalStateException(
                    "已開啟超商電子地圖（ECPAY_LOGISTICS_ENABLED=true），但 ECPAY_LOGISTICS_MERCHANT_ID、"
                            + "ECPAY_LOGISTICS_MAP_URL、SITE_URL 沒有設定完整");
        }
        siteUrl = siteUrl != null && siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
