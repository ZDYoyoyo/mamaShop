package com.mamashop.timeout.service;

import com.mamashop.order.service.AdminOrderService;
import com.mamashop.timeout.config.OrderTimeoutConfig;
import com.mamashop.timeout.config.OrderTimeoutProperties;
import com.mamashop.timeout.dto.OrderTimeoutMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 時間到了：自動完成訂單（M02 的消費者）。
 *
 * <p>真正的判斷在 {@link AdminOrderService#autoComplete}：訊息送出後的 7 天裡，
 * 家人可能已經手動完成、或改成已退回，那就什麼都不做。同一則訊息被處理兩次也一樣（冪等）。
 */
@Component
public class OrderAutoCompleteListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteListener.class);

    private final AdminOrderService adminOrderService;
    private final OrderTimeoutProperties properties;

    public OrderAutoCompleteListener(AdminOrderService adminOrderService, OrderTimeoutProperties properties) {
        this.adminOrderService = adminOrderService;
        this.properties = properties;
    }

    @RabbitListener(queues = OrderTimeoutConfig.AUTO_COMPLETE_QUEUE)
    public void handle(OrderTimeoutMessage message) {
        if (adminOrderService.autoComplete(message.orderNo(), note())) {
            log.info("訂單 {} 送達超過期限，已自動完成", message.orderNo());
        } else {
            log.info("訂單 {} 已不是「已送達」（已手動處理），略過自動完成", message.orderNo());
        }
    }

    /** 狀態紀錄的備註，例如「宅配送達超過 7 天，系統自動完成」 */
    private String note() {
        long days = properties.autoCompleteAfter().toDays();
        String after = days > 0 ? days + " 天" : properties.autoCompleteAfter().toString();
        return "宅配送達超過 " + after + "，系統自動完成";
    }
}
