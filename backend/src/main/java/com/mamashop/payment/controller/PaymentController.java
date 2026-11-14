package com.mamashop.payment.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.payment.dto.PaymentFormResponse;
import com.mamashop.payment.dto.PaymentOptionsResponse;
import com.mamashop.payment.service.PaymentService;
import com.mamashop.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 線上付款（P01） */
@Tag(name = "線上付款", description = "綠界 ECPay 信用卡付款")
@Validated
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 結帳頁用：有沒有開線上付款、付款期限幾分鐘（不用登入） */
    @Operation(summary = "付款設定")
    @GetMapping("/api/payments/options")
    public ApiResponse<PaymentOptionsResponse> options() {
        return ApiResponse.ok(paymentService.options());
    }

    /** 前往付款：回傳要 POST 到綠界的表單（只能付自己的待付款訂單） */
    @Operation(summary = "前往付款（建立綠界交易）")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/orders/{orderNo}/payment")
    public ApiResponse<PaymentFormResponse> start(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(paymentService.startPayment(authUser.id(), orderNo));
    }

    /**
     * 綠界伺服器的付款結果通知（ReturnURL）。不用登入（綠界不會帶我們的 JWT），
     * 改用檢查碼確認是綠界送的。格式是 form（application/x-www-form-urlencoded），回應是純文字 1|OK。
     */
    @Operation(summary = "綠界付款結果通知（給綠界呼叫）")
    @PostMapping(path = "/api/payments/ecpay/notify",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String ecpayNotify(@RequestParam Map<String, String> params) {
        return paymentService.handleEcpayNotification(params);
    }
}
