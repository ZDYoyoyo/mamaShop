package com.mamashop.payment.entity;

import com.mamashop.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 一次付款嘗試（P01）。每按一次「前往付款」建立一筆，見 V3__online_payment.sql */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "merchant_trade_no", nullable = false, length = 20, updatable = false)
    private String merchantTradeNo;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentAttemptStatus status;

    @Column(name = "provider_trade_no", length = 20)
    private String providerTradeNo;

    @Column(name = "payment_type", length = 30)
    private String paymentType;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_message", length = 200)
    private String resultMessage;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(Order order, String merchantTradeNo, BigDecimal amount) {
        this.order = order;
        this.merchantTradeNo = merchantTradeNo;
        this.amount = amount;
        this.status = PaymentAttemptStatus.PENDING;
    }

    /** 綠界通知的結果 */
    public void recordResult(PaymentAttemptStatus status, String providerTradeNo, String paymentType,
                             Integer resultCode, String resultMessage, Instant paidAt) {
        this.status = status;
        this.providerTradeNo = providerTradeNo;
        this.paymentType = paymentType;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getMerchantTradeNo() {
        return merchantTradeNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public String getProviderTradeNo() {
        return providerTradeNo;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public Integer getResultCode() {
        return resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
