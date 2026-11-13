package com.mamashop.order.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.service.CheckoutTokenService.Claim;
import org.springframework.stereotype.Service;

/**
 * 送出訂單＋防重複下單（R02）。
 *
 * <p>把「一次性 token 的檢查」包在 {@link OrderService#createOrder} 外面，OrderService 本身不用改：
 * <ol>
 *   <li>認領 token（Redis，原子操作）
 *   <li>呼叫 OrderService.createOrder（它自己的交易：扣庫存、建訂單，失敗整筆 rollback）
 *   <li>成功：createOrder 回傳時交易已經 commit → 把 token 標成 DONE:訂單編號
 *   <li>失敗：把 token 還原成 NEW，顧客修正後（例如改數量）可以用同一個 token 再送
 * </ol>
 *
 * <p>這個類別刻意<b>沒有</b> {@code @Transactional}：要等 createOrder 的交易 commit 之後，
 * 才把 token 標成完成。如果包在同一個交易裡，標成完成的那一刻訂單可能還沒真的寫進資料庫。
 */
@Service
public class OrderSubmissionService {

    private final CheckoutTokenService checkoutTokenService;
    private final OrderService orderService;

    public OrderSubmissionService(CheckoutTokenService checkoutTokenService, OrderService orderService) {
        this.checkoutTokenService = checkoutTokenService;
        this.orderService = orderService;
    }

    /**
     * @param replayed true：這個 token 之前已經下單成功，這次回傳的是原本那筆訂單（沒有新建）
     */
    public record Result(OrderDetailResponse order, boolean replayed) {
    }

    public Result submit(Long userId, String token, CreateOrderRequest request) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_TOKEN_REQUIRED);
        }
        Claim claim = checkoutTokenService.claim(userId, token);
        switch (claim) {
            case Claim.AlreadyDone done -> {
                // 網路重送、按了兩次：回傳原本的訂單，不要再建一筆
                return new Result(orderService.getMyOrder(userId, done.orderNo()), true);
            }
            case Claim.InProgress ignored -> throw new BusinessException(ErrorCode.ORDER_IN_PROGRESS);
            case Claim.Missing ignored -> throw new BusinessException(ErrorCode.ORDER_TOKEN_INVALID);
            case Claim.Claimed ignored -> {
                OrderDetailResponse order;
                try {
                    order = orderService.createOrder(userId, request);
                } catch (RuntimeException e) {
                    checkoutTokenService.release(userId, token);
                    throw e;
                }
                checkoutTokenService.complete(userId, token, order.orderNo());
                return new Result(order, false);
            }
            case Claim.Unavailable ignored -> {
                // Redis 壞掉：不檢查重複，照常下單（理由見 CheckoutTokenService）
                return new Result(orderService.createOrder(userId, request), false);
            }
        }
    }
}
