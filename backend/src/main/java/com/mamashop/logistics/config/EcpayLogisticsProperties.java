package com.mamashop.logistics.config;

import com.mamashop.order.dto.CvsNameRule;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 綠界物流設定，對應 application.yml 的 app.logistics.ecpay。
 *
 * <ul>
 *   <li>P02 電子地圖：只需要 merchantId</li>
 *   <li>P03 建立物流單、接收狀態通知：還需要 HashKey／HashIV（簽章）與寄件人資料</li>
 * </ul>
 * HashKey／HashIV 一律從環境變數讀：外洩的話任何人都能偽造「已取件」通知。
 *
 * @param enabled         沒開的話結帳頁手動填門市、後台手動填物流單號
 * @param merchantId      物流的商店代號
 * @param hashKey         物流的 HashKey（和金流的不一樣）
 * @param hashIv          物流的 HashIV
 * @param c2c             true：店到店（C2C）；false：大宗寄倉（B2C）
 * @param apiUrl          綠界物流網址：測試環境 https://logistics-stage.ecpay.com.tw、正式環境 https://logistics.ecpay.com.tw
 * @param siteUrl         網站網址，組出地圖回傳與狀態通知的網址
 * @param senderName      寄件人姓名（印在寄件單上，退貨會退給這個人）
 * @param senderCellPhone 寄件人手機
 */
@ConfigurationProperties(prefix = "app.logistics.ecpay")
public record EcpayLogisticsProperties(boolean enabled, String merchantId, String hashKey, String hashIv, boolean c2c,
                                       String apiUrl, String siteUrl, String senderName, String senderCellPhone) {

    private static final Pattern CELL_PHONE = Pattern.compile("^09\\d{8}$");

    public EcpayLogisticsProperties {
        if (enabled && (isBlank(merchantId) || isBlank(apiUrl) || isBlank(siteUrl))) {
            throw new IllegalStateException(
                    "已開啟綠界物流（ECPAY_LOGISTICS_ENABLED=true），但 ECPAY_LOGISTICS_MERCHANT_ID、"
                            + "ECPAY_LOGISTICS_API_URL、SITE_URL 沒有設定完整");
        }
        if (!isBlank(senderName) && !CvsNameRule.isValid(senderName)) {
            throw new IllegalStateException("ECPAY_LOGISTICS_SENDER_NAME 格式不符：中文 2～5 字或英文 4～10 字，不能有數字和符號");
        }
        if (!isBlank(senderCellPhone) && !CELL_PHONE.matcher(senderCellPhone).matches()) {
            throw new IllegalStateException("ECPAY_LOGISTICS_SENDER_CELL_PHONE 需為 09 開頭的 10 碼手機號碼");
        }
        apiUrl = stripTrailingSlash(apiUrl);
        siteUrl = stripTrailingSlash(siteUrl);
    }

    /** 電子地圖（P02）的網址 */
    public String mapUrl() {
        return apiUrl + "/Express/map";
    }

    /** 能不能建立物流單（P03）：除了地圖用的商店代號，還要有簽章用的金鑰與寄件人 */
    public boolean shipmentEnabled() {
        return enabled && !isBlank(hashKey) && !isBlank(hashIv) && !isBlank(senderName) && !isBlank(senderCellPhone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
