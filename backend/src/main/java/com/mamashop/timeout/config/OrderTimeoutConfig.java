package com.mamashop.timeout.config;

import com.mamashop.common.config.MessagingFailureConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 延遲任務：TTL＋死信佇列（M02）。目前有兩種，各用一組佇列（下面的圖以第一種為例，第二種結構完全一樣）：
 * <ol>
 *   <li>宅配「已送達」N 天後自動完成（M02）：mamashop.order-auto-complete(.wait)</li>
 *   <li>線上付款的訂單 N 分鐘沒付款自動取消（P01）：mamashop.order-payment-timeout(.wait)</li>
 * </ol>
 * 處理失敗（重試後還是失敗）的訊息會進各自的 .failed 佇列（M03，見 MessagingFailureConfig）。
 *
 * <pre>
 *  送達時 ──訊息（expiration = 7 天）──▶ 等待佇列 wait（沒有消費者，訊息就放著）
 *                                            │ 7 天到期 → RabbitMQ 把訊息「死信」轉出去
 *                                            ▼ x-dead-letter-exchange ＋ x-dead-letter-routing-key
 *                                      處理佇列 ──▶ OrderAutoCompleteListener：還是「已送達」就改成「已完成」
 * </pre>
 *
 * <ul>
 *   <li><b>TTL</b>：訊息的存活時間。這裡用「每則訊息自己的 expiration」，不用整個佇列的 x-message-ttl：
 *       佇列參數宣告後就不能改，之後把 7 天改成 10 天，後端啟動時宣告佇列會失敗（PRECONDITION_FAILED），
 *       得先到 RabbitMQ 刪掉佇列（連同裡面還在等的訊息）。每則訊息自己帶時間就沒有這個問題</li>
 *   <li><b>死信（dead letter）</b>：訊息過期、被拒絕、佇列滿了時，RabbitMQ 不直接丟掉，
 *       而是轉送到佇列設定的 dead-letter exchange。這裡拿「過期」當成「時間到了」的觸發</li>
 *   <li>兩個佇列都是 durable、訊息預設 persistent：RabbitMQ 重開後，還在等的任務不會消失</li>
 * </ul>
 *
 * <p>⚠️ RabbitMQ 只檢查<b>佇列最前面</b>那則訊息有沒有過期。同一個佇列裡的訊息延遲時間都一樣
 * （先送達的先到期）就沒問題；如果把設定從 7 天改短，舊的 7 天訊息會擋住後面的短訊息，
 * 要等它到期後面的才會一起出來。所以一種延遲時間用一個等待佇列：7 天的自動完成和 30 分鐘的未付款取消不能共用。
 */
@Configuration
@EnableConfigurationProperties(OrderTimeoutProperties.class)
public class OrderTimeoutConfig {

    public static final String EXCHANGE = "mamashop.order-timeout";
    /** 等待佇列：沒有消費者，訊息在這裡放到過期 */
    public static final String AUTO_COMPLETE_WAIT_QUEUE = "mamashop.order-auto-complete.wait";
    /** 處理佇列：過期的訊息被轉到這裡，由消費者處理 */
    public static final String AUTO_COMPLETE_QUEUE = "mamashop.order-auto-complete";
    /** 處理時重試後還是失敗（M03，見 MessagingFailureConfig） */
    public static final String AUTO_COMPLETE_FAILED_QUEUE = "mamashop.order-auto-complete.failed";
    /** 逾時未付款自動取消（P01）：和自動完成的延遲時間不同，所以另外一組等待佇列（原因見上面的 ⚠️） */
    public static final String PAYMENT_TIMEOUT_WAIT_QUEUE = "mamashop.order-payment-timeout.wait";
    public static final String PAYMENT_TIMEOUT_QUEUE = "mamashop.order-payment-timeout";
    public static final String PAYMENT_TIMEOUT_FAILED_QUEUE = "mamashop.order-payment-timeout.failed";
    public static final String PAYMENT_TIMEOUT_WAIT_KEY = "payment-timeout.wait";
    public static final String PAYMENT_TIMEOUT_KEY = "payment-timeout";
    public static final String AUTO_COMPLETE_WAIT_KEY = "auto-complete.wait";
    public static final String AUTO_COMPLETE_KEY = "auto-complete";

    @Bean
    DirectExchange orderTimeoutExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue orderAutoCompleteWaitQueue() {
        return QueueBuilder.durable(AUTO_COMPLETE_WAIT_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(AUTO_COMPLETE_KEY)
                .build();
    }

    @Bean
    Queue orderAutoCompleteQueue() {
        return QueueBuilder.durable(AUTO_COMPLETE_QUEUE).build();
    }

    @Bean
    Binding orderAutoCompleteWaitBinding(Queue orderAutoCompleteWaitQueue, DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderAutoCompleteWaitQueue).to(orderTimeoutExchange).with(AUTO_COMPLETE_WAIT_KEY);
    }

    @Bean
    Binding orderAutoCompleteBinding(Queue orderAutoCompleteQueue, DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderAutoCompleteQueue).to(orderTimeoutExchange).with(AUTO_COMPLETE_KEY);
    }

    @Bean
    Queue orderAutoCompleteFailedQueue() {
        return QueueBuilder.durable(AUTO_COMPLETE_FAILED_QUEUE).build();
    }

    @Bean
    Binding orderAutoCompleteFailedBinding(Queue orderAutoCompleteFailedQueue, TopicExchange failedExchange) {
        return BindingBuilder.bind(orderAutoCompleteFailedQueue).to(failedExchange)
                .with(MessagingFailureConfig.FAILED_ROUTING_KEY_PREFIX + AUTO_COMPLETE_KEY);
    }

    // ===== 逾時未付款自動取消（P01） =====

    @Bean
    Queue orderPaymentTimeoutWaitQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_WAIT_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_TIMEOUT_KEY)
                .build();
    }

    @Bean
    Queue orderPaymentTimeoutQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_QUEUE).build();
    }

    @Bean
    Queue orderPaymentTimeoutFailedQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_FAILED_QUEUE).build();
    }

    @Bean
    Binding orderPaymentTimeoutWaitBinding(Queue orderPaymentTimeoutWaitQueue, DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderPaymentTimeoutWaitQueue).to(orderTimeoutExchange).with(PAYMENT_TIMEOUT_WAIT_KEY);
    }

    @Bean
    Binding orderPaymentTimeoutBinding(Queue orderPaymentTimeoutQueue, DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderPaymentTimeoutQueue).to(orderTimeoutExchange).with(PAYMENT_TIMEOUT_KEY);
    }

    @Bean
    Binding orderPaymentTimeoutFailedBinding(Queue orderPaymentTimeoutFailedQueue, TopicExchange failedExchange) {
        return BindingBuilder.bind(orderPaymentTimeoutFailedQueue).to(failedExchange)
                .with(MessagingFailureConfig.FAILED_ROUTING_KEY_PREFIX + PAYMENT_TIMEOUT_KEY);
    }
}
