package com.mamashop.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 錯誤內容。
 *
 * @param code        給程式判斷用的錯誤代碼（例如 VALIDATION_FAILED），前端可以依此做不同處理
 * @param message     給使用者看的訊息（繁體中文）
 * @param fieldErrors 參數驗證失敗時，每個欄位的錯誤；其他錯誤不會有這個欄位
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    /** 單一欄位的驗證錯誤，例如 { "field": "email", "message": "Email 格式不正確" } */
    public record FieldErrorDetail(String field, String message) {
    }
}
