package com.mamashop.logistics.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.logistics.config.EcpayLogisticsProperties;
import com.mamashop.logistics.dto.CvsMapFormResponse;
import com.mamashop.logistics.dto.LogisticsOptionsResponse;
import com.mamashop.order.entity.CvsBrand;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 超商電子地圖選門市（P02，綠界物流）。
 *
 * <pre>
 *  1. 結帳頁選好超商品牌，按「選擇門市」→ {@link #mapForm}：組好參數 → 前端整頁 POST 到綠界電子地圖
 *  2. 顧客在地圖上點門市（7-ELEVEN、全家…各家自己的地圖頁）
 *  3. 地圖頁讓<b>顧客的瀏覽器</b> POST 選好的門市到 ServerReplyURL（我們的後端）→ {@link #handleMapReply}
 *  4. 後端檢查欄位格式，轉址（303）回結帳頁，門市資料放在網址參數，結帳頁填進表單
 * </pre>
 *
 * <h2>為什麼要經過後端，不直接回前端</h2>
 * 綠界是用 form POST 回傳，前端是靜態網頁（Nginx 不接受 POST 到 index.html），只能由後端接收再轉址。
 *
 * <h2>門市資料可以相信嗎</h2>
 * 這個回傳<b>沒有檢查碼</b>，是顧客的瀏覽器送的，任何人都能自己組一個。但它的意義和以前「手動填門市」一樣
 * ——就是顧客自己選的門市，填錯只影響顧客自己。所以只檢查格式（長度、字元），不當成可信的資料：
 * 下單時後端照常驗證（和手動填寫同一套規則），P03 建立物流單時綠界會再檢查門市是否存在。
 */
@Service
public class CvsMapService {

    private static final Logger log = LoggerFactory.getLogger(CvsMapService.class);

    /** 門市店號：英數字（7-11 是 6 碼數字，各家不同） */
    private static final Pattern STORE_ID = Pattern.compile("^[0-9A-Za-z]{1,20}$");
    /** 和 orders.cvs_store_name 的長度一致 */
    private static final int MAX_STORE_NAME = 50;
    private static final int MAX_ADDRESS = 100;

    private final EcpayLogisticsProperties properties;

    public CvsMapService(EcpayLogisticsProperties properties) {
        this.properties = properties;
    }

    public LogisticsOptionsResponse options() {
        return new LogisticsOptionsResponse(properties.enabled(), properties.shipmentEnabled());
    }

    /**
     * 前往電子地圖的表單。
     *
     * @param collection 是否「取貨付款」：地圖只顯示可以代收貨款的門市（貨到付款要 true，線上付款 false）
     * @param mobile     手機版地圖
     */
    public CvsMapFormResponse mapForm(CvsBrand brand, boolean collection, boolean mobile) {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.CVS_MAP_UNAVAILABLE);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MerchantID", properties.merchantId());
        fields.put("LogisticsType", "CVS");
        fields.put("LogisticsSubType", subType(brand));
        fields.put("IsCollection", collection ? "Y" : "N");
        fields.put("ServerReplyURL", properties.siteUrl() + "/api/logistics/ecpay/cvs-map-reply");
        fields.put("Device", mobile ? "1" : "0");
        return new CvsMapFormResponse(properties.mapUrl(), fields);
    }

    /**
     * 地圖選好門市後的回傳 → 回結帳頁的網址。格式不對就回結帳頁並帶 cvsMapError（請顧客再選一次）。
     */
    public URI handleMapReply(Map<String, String> params) {
        String checkoutUrl = properties.siteUrl() + "/checkout";
        CvsBrand brand = brandOf(params.get("LogisticsSubType"));
        String storeId = trim(params.get("CVSStoreID"));
        String storeName = clean(params.get("CVSStoreName"));
        String address = clean(params.get("CVSAddress"));
        if (!properties.enabled() || brand == null || storeId == null || !STORE_ID.matcher(storeId).matches()
                || storeName == null || storeName.isEmpty() || storeName.length() > MAX_STORE_NAME) {
            log.warn("電子地圖回傳的門市資料格式不正確：{}", params);
            return UriComponentsBuilder.fromUriString(checkoutUrl).queryParam("cvsMapError", "1").build().toUri();
        }
        return UriComponentsBuilder.fromUriString(checkoutUrl)
                .queryParam("cvsBrand", brand.name())
                .queryParam("cvsStoreCode", storeId)
                .queryParam("cvsStoreName", storeName)
                .queryParam("cvsStoreAddress", address == null ? "" : truncate(address, MAX_ADDRESS))
                // encode：中文與特殊字元轉成 %XX，網址參數不會被拆壞
                .encode()
                .build()
                .toUri();
    }

    /** 我們的超商品牌 → 綠界的物流子類型（店到店多一個 C2C 後綴） */
    public String subType(CvsBrand brand) {
        String base = switch (brand) {
            case SEVEN -> "UNIMART";
            case FAMILY -> "FAMI";
            case HILIFE -> "HILIFE";
            // OK 超商店到店已於 2026/7/1 停止服務（綠界物流貨態代碼表的異動歷程），大宗寄倉本來就沒有
            case OK -> throw new BusinessException(ErrorCode.CVS_BRAND_UNAVAILABLE);
        };
        return properties.c2c() ? base + "C2C" : base;
    }

    private static CvsBrand brandOf(String subType) {
        if (subType == null) {
            return null;
        }
        return switch (subType.replace("C2C", "")) {
            case "UNIMART" -> CvsBrand.SEVEN;
            case "FAMI" -> CvsBrand.FAMILY;
            case "HILIFE" -> CvsBrand.HILIFE;
            default -> null; // OKMART 已停止服務
        };
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 去掉換行、控制字元（只是顯示用的文字，不該有這些） */
    private static String clean(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "").trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
