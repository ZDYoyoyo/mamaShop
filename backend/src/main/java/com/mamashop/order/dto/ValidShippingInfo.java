package com.mamashop.order.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自訂的 Bean Validation 註解：依運送方式檢查收件資訊。
 * 實際規則在 {@link ShippingInfoValidator}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ShippingInfoValidator.class)
public @interface ValidShippingInfo {

    String message() default "收件資訊不完整";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
