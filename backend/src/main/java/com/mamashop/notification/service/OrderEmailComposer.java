package com.mamashop.notification.service;

import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.event.OrderEventType;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 組出通知信的主旨與內文（純文字）。沒有任何外部依賴，方便單元測試。
 *
 * <p>刻意不放收件地址、電話：信可能被轉寄，個資越少越好，顧客點連結到網站上看完整內容（要登入）。
 */
public final class OrderEmailComposer {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TAIPEI);
    private static final String LINE = "────────────────────";

    private OrderEmailComposer() {
    }

    public record Email(String to, String subject, String body) {
    }

    public static Email compose(OrderEventType type, AdminOrderDetailResponse detail, String siteUrl) {
        OrderDetailResponse order = detail.order();
        String subject = switch (type) {
            case CREATED -> "[mamaShop] 訂單已成立（" + order.orderNo() + "）";
            case CONFIRMED -> "[mamaShop] 訂單已確認，正在備貨（" + order.orderNo() + "）";
            case SHIPPED -> "[mamaShop] 訂單已出貨（" + order.orderNo() + "）";
        };

        boolean online = order.paymentMethod() == PaymentMethod.ONLINE;
        StringBuilder body = new StringBuilder();
        body.append(detail.customer().name()).append(" 您好：\n\n");
        body.append(switch (type) {
            case CREATED -> online
                    ? "感謝您的訂購！請盡快完成線上付款，逾時未付款訂單會自動取消。可以從下面的「查看訂單」前往付款。\n"
                    : "感謝您的訂購！我們已經收到您的訂單，確認後會再通知您。\n";
            case CONFIRMED -> online
                    ? "我們已經收到您的付款，正在為您備貨，出貨時會再通知您。\n"
                    : "您的訂單已確認，我們正在為您備貨，出貨時會再通知您。\n";
            case SHIPPED -> "您的訂單已經出貨了！\n";
        });
        body.append('\n');
        body.append("訂單編號：").append(order.orderNo()).append('\n');
        body.append("下單時間：").append(TIME.format(order.createdAt())).append('\n');

        if (type == OrderEventType.SHIPPED) {
            body.append("物流單號：").append(order.trackingNo() == null ? "（未提供）" : order.trackingNo()).append('\n');
            body.append(order.shippingMethod() == ShippingMethodCode.CVS_PICKUP
                    ? "包裹到達門市後會有簡訊通知，請於 7 天內取件" + (online ? "。\n" : "並付款。\n")
                    : "請留意宅配人員的聯絡" + (online ? "。\n" : "，收到商品時付款。\n"));
        }

        body.append(LINE).append('\n');
        order.items().forEach(item -> body.append(item.productName()).append(" × ").append(item.quantity())
                .append("　").append(money(item.subtotal())).append('\n'));
        body.append(LINE).append('\n');
        body.append("商品小計：").append(money(order.itemsAmount())).append('\n');
        body.append("運費：").append(money(order.shippingFee())).append('\n');
        body.append("總計：").append(money(order.totalAmount()))
                .append(online ? "（線上付款）\n" : "（貨到付款）\n");
        body.append("運送方式：").append(order.shippingMethod() == ShippingMethodCode.CVS_PICKUP ? "超商取貨" : "宅配").append('\n');
        body.append('\n');
        body.append("查看訂單：").append(stripTrailingSlash(siteUrl)).append("/orders/").append(order.orderNo()).append('\n');
        body.append('\n');
        body.append("這是系統自動寄出的信，請不要直接回覆。\n");
        body.append("mamaShop\n");

        return new Email(detail.customer().email(), subject, body.toString());
    }

    /** NT$ 1,280；有小數才顯示（和前端的 formatPrice 一致） */
    static String money(BigDecimal amount) {
        // DecimalFormat 不是 thread-safe，每次建立新的
        return "NT$ " + new DecimalFormat("#,##0.##").format(amount);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
