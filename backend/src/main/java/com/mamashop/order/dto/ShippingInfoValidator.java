package com.mamashop.order.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

/**
 * 宅配：必填地址。超商取貨：必填超商品牌、店號、店名。
 *
 * <p>錯誤會掛在對應的欄位上（addPropertyNode），所以前端收到的 fieldErrors 是
 * { "field": "shippingAddress", "message": "宅配需填寫收件地址" }，可以直接標在輸入框旁邊。
 * 資料庫的 ck_orders_shipping_info 也有同樣的檢查，這裡是給使用者看得懂的第一道防線。
 */
public class ShippingInfoValidator implements ConstraintValidator<ValidShippingInfo, CreateOrderRequest> {

    @Override
    public boolean isValid(CreateOrderRequest request, ConstraintValidatorContext context) {
        if (request == null || request.shippingMethod() == null) {
            return true; // shippingMethod 本身的 @NotNull 會另外報錯
        }
        context.disableDefaultConstraintViolation();

        boolean valid = true;
        switch (request.shippingMethod()) {
            case HOME_DELIVERY -> {
                if (!StringUtils.hasText(request.shippingAddress())) {
                    valid = addError(context, "shippingAddress", "宅配需填寫收件地址");
                }
            }
            case CVS_PICKUP -> {
                if (request.cvsBrand() == null) {
                    valid = addError(context, "cvsBrand", "超商取貨需選擇超商");
                }
                if (!StringUtils.hasText(request.cvsStoreCode())) {
                    valid = addError(context, "cvsStoreCode", "超商取貨需填寫門市店號");
                }
                if (!StringUtils.hasText(request.cvsStoreName())) {
                    valid = addError(context, "cvsStoreName", "超商取貨需填寫門市名稱");
                }
            }
        }
        return valid;
    }

    private static boolean addError(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
