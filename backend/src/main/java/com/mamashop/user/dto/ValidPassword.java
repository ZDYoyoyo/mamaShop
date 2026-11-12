package com.mamashop.user.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 密碼規則（組合註解）：8～72 個英文字母、數字或符號。
 * 註冊、新增管理員、重設密碼共用同一條規則，要改只改這裡。
 *
 * <p>為什麼上限 72、不能用中文：BCrypt 只處理前 72 bytes，中文一個字佔 3 bytes。
 * 必填請另外加 @NotBlank（null 不會被 @Pattern 檢查）。
 */
@Pattern(regexp = "^[\\x21-\\x7E]{8,72}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "需為 8 到 72 個英文字母、數字或符號";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
