-- =====================================================================
-- P01 第三方線上金流（綠界 ECPay）
-- =====================================================================

-- 訂單多一種付款方式：ONLINE（線上付款，訂單從 PENDING_PAYMENT 開始）
ALTER TABLE orders DROP CONSTRAINT ck_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('COD', 'ONLINE'));

-- ---------------------------------------------------------------------
-- 付款紀錄：每按一次「前往付款」建立一筆。
-- 綠界的特店交易編號（MerchantTradeNo）不能重複使用，顧客關掉付款頁再付一次就要新的編號，
-- 所以「一張訂單、多次付款嘗試」，不直接把付款欄位放在 orders。
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGSERIAL     PRIMARY KEY,
    order_id            BIGINT        NOT NULL,
    -- 送給綠界的交易編號：訂單編號＋兩位數序號，例如 M261114123456789001（最多 20 碼英數）
    merchant_trade_no   VARCHAR(20)   NOT NULL,
    amount              NUMERIC(10,2) NOT NULL,
    -- PENDING：已建立、等綠界通知；PAID：付款成功；FAILED：綠界通知失敗
    status              VARCHAR(20)   NOT NULL,
    -- 以下是綠界通知（ReturnURL）帶回來的資料
    provider_trade_no   VARCHAR(20),               -- 綠界的交易編號（TradeNo），退款、查帳時用
    payment_type        VARCHAR(30),               -- 例如 Credit_CreditCard
    result_code         INTEGER,                   -- RtnCode，1 = 成功
    result_message      VARCHAR(200),              -- RtnMsg
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_payments_merchant_trade_no UNIQUE (merchant_trade_no),
    CONSTRAINT fk_payments_order  FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED'))
);

CREATE INDEX idx_payments_order ON payments (order_id);
