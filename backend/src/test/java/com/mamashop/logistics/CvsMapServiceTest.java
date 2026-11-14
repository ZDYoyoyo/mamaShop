package com.mamashop.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamashop.common.exception.ErrorCode;
import com.mamashop.logistics.config.EcpayLogisticsProperties;
import com.mamashop.logistics.dto.CvsMapFormResponse;
import com.mamashop.logistics.service.CvsMapService;
import com.mamashop.order.entity.CvsBrand;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

/** 超商電子地圖（P02）：組地圖參數、檢查地圖回傳的門市資料 */
class CvsMapServiceTest {

    private static final String API_URL = "https://logistics-stage.ecpay.com.tw";
    private static final String MAP_URL = API_URL + "/Express/map";

    private final CvsMapService c2c = service(true, true);

    @Test
    void mapForm_c2c_mapsBrandToEcpaySubType() {
        CvsMapFormResponse form = c2c.mapForm(CvsBrand.SEVEN, true, true);

        assertThat(form.action()).isEqualTo(MAP_URL);
        assertThat(form.fields())
                .containsEntry("MerchantID", "2000933")
                .containsEntry("LogisticsType", "CVS")
                .containsEntry("LogisticsSubType", "UNIMARTC2C")
                .containsEntry("IsCollection", "Y")
                .containsEntry("ServerReplyURL", "https://shop.example.com/api/logistics/ecpay/cvs-map-reply")
                .containsEntry("Device", "1");
        assertThat(c2c.mapForm(CvsBrand.FAMILY, false, false).fields())
                .containsEntry("LogisticsSubType", "FAMIC2C")
                .containsEntry("IsCollection", "N") // 線上付款：不用代收貨款
                .containsEntry("Device", "0");
    }

    @Test
    void mapForm_b2c_hasNoSuffix() {
        assertThat(service(true, false).mapForm(CvsBrand.HILIFE, true, false).fields())
                .containsEntry("LogisticsSubType", "HILIFE");
    }

    @Test
    void okMart_isNoLongerAvailable() {
        // OK 超商店到店已於 2026/7/1 停止服務
        assertThatThrownBy(() -> c2c.mapForm(CvsBrand.OK, true, false))
                .extracting("errorCode").isEqualTo(ErrorCode.CVS_BRAND_UNAVAILABLE);
    }

    @Test
    void mapForm_disabled_isRejected() {
        assertThatThrownBy(() -> service(false, true).mapForm(CvsBrand.SEVEN, true, false))
                .extracting("errorCode").isEqualTo(ErrorCode.CVS_MAP_UNAVAILABLE);
    }

    @Test
    void reply_redirectsToCheckoutWithStore_encoded() {
        URI uri = c2c.handleMapReply(reply("UNIMARTC2C", "131386", "建盛門市", "台北市中正區重慶南路一段1號"));

        assertThat(uri.toString()).startsWith("https://shop.example.com/checkout?");
        Map<String, String> query = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
        assertThat(decode(query.get("cvsBrand"))).isEqualTo("SEVEN");
        assertThat(decode(query.get("cvsStoreCode"))).isEqualTo("131386");
        assertThat(decode(query.get("cvsStoreName"))).isEqualTo("建盛門市");
        assertThat(decode(query.get("cvsStoreAddress"))).isEqualTo("台北市中正區重慶南路一段1號");
        // 中文有被編碼，網址裡沒有原始的中文或空白
        assertThat(uri.getRawQuery()).doesNotContain("門市").doesNotContain(" ");
    }

    @Test
    void reply_withBadData_redirectsWithError() {
        // 店號有奇怪的字元（例如有人自己組一個 POST 想塞東西進網址）
        assertThat(c2c.handleMapReply(reply("UNIMARTC2C", "13&x=<script>", "門市", "")).toString())
                .isEqualTo("https://shop.example.com/checkout?cvsMapError=1");
        // 不認得的物流類型、沒有門市名稱、門市名稱太長
        assertThat(c2c.handleMapReply(reply("TCAT", "131386", "門市", "")).getQuery()).isEqualTo("cvsMapError=1");
        assertThat(c2c.handleMapReply(reply("FAMIC2C", "006598", " ", "")).getQuery()).isEqualTo("cvsMapError=1");
        assertThat(c2c.handleMapReply(reply("FAMIC2C", "006598", "門".repeat(51), "")).getQuery())
                .isEqualTo("cvsMapError=1");
    }

    @Test
    void reply_stripsControlCharacters() {
        URI uri = c2c.handleMapReply(reply("FAMIC2C", "006598", "全家\r\n測試店", "地址\t1號"));

        Map<String, String> query = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
        assertThat(decode(query.get("cvsBrand"))).isEqualTo("FAMILY");
        assertThat(decode(query.get("cvsStoreName"))).isEqualTo("全家測試店");
        assertThat(decode(query.get("cvsStoreAddress"))).isEqualTo("地址1號");
    }

    private static Map<String, String> reply(String subType, String storeId, String storeName, String address) {
        Map<String, String> params = new HashMap<>();
        params.put("MerchantID", "2000933");
        params.put("MerchantTradeNo", "");
        params.put("LogisticsSubType", subType);
        params.put("CVSStoreID", storeId);
        params.put("CVSStoreName", storeName);
        params.put("CVSAddress", address);
        params.put("CVSTelephone", "");
        params.put("CVSOutSide", "0");
        params.put("ExtraData", "");
        return params;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static CvsMapService service(boolean enabled, boolean c2c) {
        return new CvsMapService(new EcpayLogisticsProperties(enabled, "2000933", null, null, c2c, API_URL + "/",
                "https://shop.example.com/", null, null));
    }
}
