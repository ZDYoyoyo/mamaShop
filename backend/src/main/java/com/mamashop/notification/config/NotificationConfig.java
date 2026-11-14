package com.mamashop.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 訂單通知的 RabbitMQ 設定（M01）。
 *
 * <pre>
 *   後端（生產者）──訊息＋routing key──▶ Exchange ──依 binding 規則──▶ Queue ──▶ 後端（消費者）寄信
 *                    order.created        mamashop.orders   order.*       mamashop.order-notifications
 * </pre>
 *
 * <ul>
 *   <li><b>Exchange（交換器）</b>：生產者只把訊息送到 exchange，不直接送到 queue。
 *       topic exchange 依 routing key 的規則（{@code order.*}）決定送到哪些 queue</li>
 *   <li><b>Queue（佇列）</b>：訊息排隊等消費者處理。durable：RabbitMQ 重開後 queue 還在</li>
 *   <li><b>Binding</b>：exchange 與 queue 的對應規則</li>
 * </ul>
 * 好處：之後要加「出貨時通知物流」只要新增一個 queue 綁到 order.shipped，生產者完全不用改。
 *
 * <p>這些 Bean 在後端啟動、第一次連上 RabbitMQ 時自動建立（RabbitAdmin），不用手動到管理介面建。
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    public static final String EXCHANGE = "mamashop.orders";
    public static final String QUEUE = "mamashop.order-notifications";
    /** routing key 的前綴：order.created、order.confirmed、order.shipped */
    public static final String ROUTING_KEY_PREFIX = "order.";

    @Bean
    TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue orderNotificationQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding orderNotificationBinding(Queue orderNotificationQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderNotificationQueue).to(orderExchange).with(ROUTING_KEY_PREFIX + "*");
    }

    /** 訊息用 JSON（Jackson 3）傳送，在管理介面也看得懂；Spring Boot 會自動用在 RabbitTemplate 與 @RabbitListener */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
