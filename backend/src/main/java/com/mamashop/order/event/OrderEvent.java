package com.mamashop.order.event;

/**
 * 訂單的領域事件（M01）：訂單發生了顧客需要知道的事。
 *
 * <p>在交易裡用 Spring 的 ApplicationEventPublisher 發出。訂單模組只負責「發出事件」，
 * 不知道誰會處理（目前是 notification 模組寄 Email）。之後要加簡訊、LINE 通知，訂單的程式不用改。
 *
 * <p>收到事件的一方要用 {@code @TransactionalEventListener}（預設 AFTER_COMMIT）：
 * 交易 commit 之後才處理。交易 rollback（例如庫存不足下單失敗）就不會收到，
 * 不會發生「顧客收到訂單成立的信，但訂單其實不存在」。
 *
 * @param orderNo 訂單編號
 * @param type    發生了什麼事
 */
public record OrderEvent(String orderNo, OrderEventType type) {
}
