package com.mamashop.notification.service;

import com.mamashop.notification.config.NotificationProperties;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.event.OrderEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 寄出訂單通知信（M01）。
 *
 * <p>還沒設定寄信伺服器（MAIL_HOST 空白，例如剛上線還沒選好寄信服務）時只記 log、不寄，
 * 不讓整個通知流程報錯。
 */
@Component
public class OrderEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;
    private final String mailHost;

    public OrderEmailSender(ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties,
                            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailHost = mailHost;
    }

    public void send(OrderEventType type, AdminOrderDetailResponse detail) {
        OrderEmailComposer.Email email = OrderEmailComposer.compose(type, detail, properties.siteUrl());
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost.isBlank() || sender == null) {
            log.info("沒有設定寄信伺服器（MAIL_HOST），略過寄信：{}", email.subject());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.body());
        sender.send(message);
        log.info("已寄出通知信：{}", email.subject());
    }
}
