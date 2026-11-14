package com.mamashop.logistics.entity;

import com.mamashop.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 綠界物流單（P03），見 V4__shipments.sql */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "merchant_trade_no", nullable = false, length = 20, updatable = false)
    private String merchantTradeNo;

    @Column(name = "logistics_id", nullable = false, length = 20, updatable = false)
    private String logisticsId;

    @Column(name = "logistics_sub_type", nullable = false, length = 20, updatable = false)
    private String logisticsSubType;

    @Column(name = "cvs_payment_no", length = 20)
    private String cvsPaymentNo;

    @Column(name = "cvs_validation_no", length = 10)
    private String cvsValidationNo;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "status_message", length = 200)
    private String statusMessage;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {
    }

    public Shipment(Order order, String merchantTradeNo, String logisticsId, String logisticsSubType,
                    String cvsPaymentNo, String cvsValidationNo) {
        this.order = order;
        this.merchantTradeNo = merchantTradeNo;
        this.logisticsId = logisticsId;
        this.logisticsSubType = logisticsSubType;
        this.cvsPaymentNo = cvsPaymentNo;
        this.cvsValidationNo = cvsValidationNo;
    }

    public void updateStatus(int code, String message, Instant at) {
        this.statusCode = code;
        this.statusMessage = message;
        this.statusUpdatedAt = at;
    }

    /** 寄件代碼：7-ELEVEN 是「寄件代碼＋驗證碼」，其他家只有寄件代碼。也當作顧客看到的物流單號 */
    public String trackingNo() {
        return cvsValidationNo == null || cvsValidationNo.isEmpty() ? cvsPaymentNo : cvsPaymentNo + cvsValidationNo;
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

    public String getLogisticsId() {
        return logisticsId;
    }

    public String getLogisticsSubType() {
        return logisticsSubType;
    }

    public String getCvsPaymentNo() {
        return cvsPaymentNo;
    }

    public String getCvsValidationNo() {
        return cvsValidationNo;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
