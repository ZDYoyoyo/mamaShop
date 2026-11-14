package com.mamashop.common.config;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 訊息處理失敗的最後一站：失敗佇列（M03）。
 *
 * <pre>
 *  消費者丟例外 ──▶ 重試（application.yml 的 listener.simple.retry：等 2 秒、6 秒，共 3 次）
 *                     │ 還是失敗
 *                     ▼ MessageRecoverer（這裡的 Bean，Spring Boot 自動接到 @RabbitListener 上）
 *  exchange mamashop.failed（topic）、routing key「error.」＋原本的 routing key
 *     ├─ error.order.*       ──▶ mamashop.order-notifications.failed   （NotificationConfig 宣告）
 *     └─ error.auto-complete ──▶ mamashop.order-auto-complete.failed   （OrderTimeoutConfig 宣告）
 * </pre>
 *
 * <ul>
 *   <li><b>為什麼不直接用佇列的 dead-letter（x-dead-letter-exchange）</b>：
 *       已經存在的佇列不能改參數（要刪掉重建）；而且死信只知道「被拒絕」，
 *       RepublishMessageRecoverer 會在訊息 header 加上<b>錯誤訊息與 stack trace</b>
 *       （x-exception-message、x-exception-stacktrace），之後查原因、修好再重送都方便</li>
 *   <li><b>每個來源一個失敗佇列</b>：修好之後要「重送」時，整個失敗佇列搬回原本的佇列就好（見 docs/NOTES.md 的 M03）</li>
 *   <li>移到失敗佇列時記 ERROR log，不是默默消失</li>
 * </ul>
 */
@Configuration
public class MessagingFailureConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingFailureConfig.class);

    public static final String FAILED_EXCHANGE = "mamashop.failed";
    /** 失敗訊息的 routing key 前綴：order.created → error.order.created */
    public static final String FAILED_ROUTING_KEY_PREFIX = "error.";

    @Bean
    TopicExchange failedExchange() {
        return new TopicExchange(FAILED_EXCHANGE, true, false);
    }

    @Bean
    MessageRecoverer failedMessageRecoverer(RabbitTemplate rabbitTemplate) {
        RepublishMessageRecoverer republish = new RepublishMessageRecoverer(rabbitTemplate, FAILED_EXCHANGE)
                .errorRoutingKeyPrefix(FAILED_ROUTING_KEY_PREFIX);
        return (message, cause) -> {
            republish.recover(message, cause);
            // 訊息內容只有訂單編號與事件種類（沒有個資），記在 log 裡方便查是哪一筆
            log.error("訊息重試後仍然失敗，已移到失敗佇列（routing key {}，內容 {}）：{}",
                    message.getMessageProperties().getReceivedRoutingKey(),
                    new String(message.getBody(), StandardCharsets.UTF_8), rootMessage(cause));
        };
    }

    private static String rootMessage(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.toString();
    }
}
