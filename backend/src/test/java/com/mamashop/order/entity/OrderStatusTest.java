package com.mamashop.order.entity;

import static com.mamashop.order.entity.OrderStatus.CANCELLED;
import static com.mamashop.order.entity.OrderStatus.COMPLETED;
import static com.mamashop.order.entity.OrderStatus.DELIVERED;
import static com.mamashop.order.entity.OrderStatus.PENDING_CONFIRM;
import static com.mamashop.order.entity.OrderStatus.PENDING_PAYMENT;
import static com.mamashop.order.entity.OrderStatus.PROCESSING;
import static com.mamashop.order.entity.OrderStatus.RETURNED;
import static com.mamashop.order.entity.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamashop.common.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 狀態機規則（純單元測試，不需要 Spring 與資料庫） */
class OrderStatusTest {

    @Test
    void normalFlow_isAllowed() {
        assertThat(PENDING_CONFIRM.canTransitionTo(PROCESSING)).isTrue();
        assertThat(PENDING_PAYMENT.canTransitionTo(PROCESSING)).isTrue();
        assertThat(PROCESSING.canTransitionTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canTransitionTo(DELIVERED)).isTrue();
        assertThat(DELIVERED.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void cancel_onlyBeforeShipping() {
        assertThat(PENDING_CONFIRM.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PROCESSING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(SHIPPED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(DELIVERED.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    void return_onlyAfterShipping() {
        assertThat(SHIPPED.canTransitionTo(RETURNED)).isTrue();
        assertThat(DELIVERED.canTransitionTo(RETURNED)).isTrue();
        assertThat(PROCESSING.canTransitionTo(RETURNED)).isFalse();
    }

    @Test
    void cannotSkipSteps_orGoBackwards() {
        assertThat(PENDING_CONFIRM.canTransitionTo(SHIPPED)).isFalse();
        assertThat(PROCESSING.canTransitionTo(COMPLETED)).isFalse();
        assertThat(SHIPPED.canTransitionTo(PROCESSING)).isFalse();
    }

    @Test
    void finalStates_cannotChange() {
        for (OrderStatus finalStatus : new OrderStatus[]{COMPLETED, CANCELLED, RETURNED}) {
            assertThat(finalStatus.isFinal()).isTrue();
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(finalStatus.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void customerCanOnlyCancelBeforeConfirmation() {
        assertThat(PENDING_CONFIRM.isCancellableByCustomer()).isTrue();
        assertThat(PENDING_PAYMENT.isCancellableByCustomer()).isTrue();
        assertThat(PROCESSING.isCancellableByCustomer()).isFalse();
    }

    @Test
    void everyStatusHasTransitionRules() {
        // 新增狀態時忘了設定規則，這個測試會失敗（canTransitionTo 會 NullPointerException）
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(CANCELLED)).isIn(true, false);
            assertThat(status.label()).isNotBlank();
        }
    }

    @Test
    void orderTransitionTo_setsTimestamp_andRejectsInvalidChange() {
        Order order = new Order();
        order.setStatus(PENDING_CONFIRM);
        Instant now = Instant.parse("2026-11-12T03:00:00Z");

        OrderStatus from = order.transitionTo(PROCESSING, now);

        assertThat(from).isEqualTo(PENDING_CONFIRM);
        assertThat(order.getStatus()).isEqualTo(PROCESSING);
        assertThat(order.getConfirmedAt()).isEqualTo(now);
        assertThatThrownBy(() -> order.transitionTo(COMPLETED, now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("備貨中")
                .hasMessageContaining("已完成");
    }
}
