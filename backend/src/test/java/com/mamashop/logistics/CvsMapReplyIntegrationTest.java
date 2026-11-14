package com.mamashop.logistics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 電子地圖回傳（P02）：綠界地圖頁讓顧客的瀏覽器 POST form 到後端（不帶 JWT），後端 303 轉址回結帳頁。
 */
@SpringBootTest(properties = {
        "app.logistics.ecpay.enabled=true",
        "app.logistics.ecpay.merchant-id=2000933",
        "app.logistics.ecpay.site-url=https://shop.example.com"})
@AutoConfigureMockMvc
class CvsMapReplyIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void reply_redirectsBrowserBackToCheckout() throws Exception {
        mockMvc.perform(post("/api/logistics/ecpay/cvs-map-reply")
                        // 真的瀏覽器從綠界的頁面 POST 過來會帶 Origin（一開始就是這個讓 CORS 擋成 403，實測才發現）
                        .header("Origin", "https://logistics-stage.ecpay.com.tw")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("MerchantID", "2000933")
                        .param("LogisticsSubType", "UNIMARTC2C")
                        .param("CVSStoreID", "131386")
                        .param("CVSStoreName", "建盛門市")
                        .param("CVSAddress", "台北市中正區"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", Matchers.startsWith(
                        "https://shop.example.com/checkout?cvsBrand=SEVEN&cvsStoreCode=131386&cvsStoreName=%E5%BB%BA")));
    }

    /** 其他 API 的 CORS 照樣有效：別的網站的 JavaScript 不能呼叫 */
    @Test
    void otherApis_stillRejectForeignOrigins() throws Exception {
        mockMvc.perform(get("/api/logistics/options").header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
