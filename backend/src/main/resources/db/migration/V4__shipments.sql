-- =====================================================================
-- P03 物流單（綠界超商店到店）
-- 一張訂單通常只有一筆；綠界建立失敗不會寫入。留著每次的編號，方便到綠界後台查詢。
-- =====================================================================
CREATE TABLE shipments (
    id                  BIGSERIAL     PRIMARY KEY,
    order_id            BIGINT        NOT NULL,
    -- 送給綠界的交易編號：訂單編號＋兩位數序號（最多 20 碼英數）
    merchant_trade_no   VARCHAR(20)   NOT NULL,
    -- 綠界的物流交易編號（AllPayLogisticsID），狀態通知用它對應
    logistics_id        VARCHAR(20)   NOT NULL,
    logistics_sub_type  VARCHAR(20)   NOT NULL,     -- UNIMARTC2C、FAMIC2C、HILIFEC2C
    -- 寄件代碼：到超商寄件（7-11 ibon／全家 FamiPort）或列印寄件單用
    cvs_payment_no      VARCHAR(20),
    cvs_validation_no   VARCHAR(10),                -- 驗證碼（只有 7-ELEVEN）
    -- 最新的物流狀態（綠界的 RtnCode／RtnMsg），每次通知更新
    status_code         INTEGER       NOT NULL,
    status_message      VARCHAR(200),
    status_updated_at   TIMESTAMPTZ   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_shipments_merchant_trade_no UNIQUE (merchant_trade_no),
    CONSTRAINT uk_shipments_logistics_id      UNIQUE (logistics_id),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_shipments_order ON shipments (order_id);
