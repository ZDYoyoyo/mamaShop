package com.mamashop.common.ecpay;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 回給綠界伺服器的通知回應（例如 1|OK）。
 *
 * <p>不能在 {@code @PostMapping} 寫 {@code produces = text/plain}：那會讓 Spring 拿它和請求的 Accept 比對，
 * 綠界送通知時帶的 Accept 不一定包含 text/plain，比對不到就回 406，綠界收不到 1|OK、我們也沒有處理通知
 * （展示站實際發生過：付款成功但訂單一直停在待付款）。
 *
 * <p>這裡直接在回應裡指定 Content-Type：已經指定的話 Spring 就不再依 Accept 協商，什麼 Accept 都回純文字。
 */
public final class EcpayReply {

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private EcpayReply() {
    }

    public static ResponseEntity<String> plainText(String body) {
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(body);
    }
}
