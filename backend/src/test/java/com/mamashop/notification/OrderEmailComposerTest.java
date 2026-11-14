package com.mamashop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.notification.service.OrderEmailComposer;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.event.OrderEventType;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 通知信的內容（M01） */
class OrderEmailComposerTest {

    @Test
    void created_containsOrderSummaryAndLink_butNoAddressOrPhone() {
        var email = OrderEmailComposer.compose(OrderEventType.CREATED,
                detail(ShippingMethodCode.HOME_DELIVERY, null), "https://shop.example/");

        assertThat(email.to()).isEqualTo("ming@example.com");
        assertThat(email.subject()).isEqualTo("[mamaShop] 訂單已成立（M2611130000000001）");
        assertThat(email.body())
                .contains("王小明 您好")
                .contains("訂單編號：M2611130000000001")
                .contains("下單時間：2026/11/13 10:30") // UTC 02:30 → 台灣 10:30
                .contains("帆布托特包 × 2　NT$ 1,780")
                .contains("運費：NT$ 100")
                .contains("總計：NT$ 1,880（貨到付款）")
                .contains("查看訂單：https://shop.example/orders/M2611130000000001")
                // 信可能被轉寄：不放收件地址、電話
                .doesNotContain("台北市中正區")
                .doesNotContain("0912345678");
    }

    @Test
    void shipped_cvs_showsTrackingNoAndPickupHint() {
        var email = OrderEmailComposer.compose(OrderEventType.SHIPPED,
                detail(ShippingMethodCode.CVS_PICKUP, "T12345"), "https://shop.example");

        assertThat(email.subject()).isEqualTo("[mamaShop] 訂單已出貨（M2611130000000001）");
        assertThat(email.body()).contains("物流單號：T12345").contains("7 天內取件").contains("運送方式：超商取貨");
    }

    @Test
    void confirmed() {
        var email = OrderEmailComposer.compose(OrderEventType.CONFIRMED,
                detail(ShippingMethodCode.HOME_DELIVERY, null), "https://shop.example");

        assertThat(email.subject()).contains("訂單已確認");
        assertThat(email.body()).contains("正在為您備貨").doesNotContain("物流單號");
    }

    @Test
    void onlinePayment_textsMentionPaymentInsteadOfCashOnDelivery() {
        String created = OrderEmailComposer.compose(OrderEventType.CREATED,
                detail(ShippingMethodCode.HOME_DELIVERY, null, PaymentMethod.ONLINE), "https://shop.example").body();
        assertThat(created).contains("請盡快完成線上付款").contains("（線上付款）").doesNotContain("貨到付款");

        String confirmed = OrderEmailComposer.compose(OrderEventType.CONFIRMED,
                detail(ShippingMethodCode.HOME_DELIVERY, null, PaymentMethod.ONLINE), "https://shop.example").body();
        assertThat(confirmed).contains("已經收到您的付款");

        String shipped = OrderEmailComposer.compose(OrderEventType.SHIPPED,
                detail(ShippingMethodCode.CVS_PICKUP, "T1", PaymentMethod.ONLINE), "https://shop.example").body();
        assertThat(shipped).contains("7 天內取件").doesNotContain("並付款");
    }

    private static AdminOrderDetailResponse detail(ShippingMethodCode shippingMethod, String trackingNo) {
        return detail(shippingMethod, trackingNo, PaymentMethod.COD);
    }

    private static AdminOrderDetailResponse detail(ShippingMethodCode shippingMethod, String trackingNo,
                                                   PaymentMethod paymentMethod) {
        OrderDetailResponse order = new OrderDetailResponse("M2611130000000001", OrderStatus.PENDING_CONFIRM,
                paymentMethod, PaymentStatus.UNPAID, shippingMethod, "王媽媽", "0912345678", "台北市中正區",
                null, null, null, null, trackingNo,
                new BigDecimal("1780.00"), new BigDecimal("100.00"), new BigDecimal("1880.00"),
                List.of(new OrderDetailResponse.ItemResponse(1L, "帆布托特包", new BigDecimal("890.00"), 2,
                        new BigDecimal("1780.00"))),
                Instant.parse("2026-11-13T02:30:00Z"), null, null, null, null, null, null, null);
        return new AdminOrderDetailResponse(order,
                new AdminOrderDetailResponse.CustomerInfo(9L, "王小明", "ming@example.com", "0911111111"), List.of(), 0);
    }
}
