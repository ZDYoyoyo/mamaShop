package com.mamashop.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 所有 API 統一的回應格式。
 *
 * <pre>
 * 成功：{ "success": true,  "data": { ... } }
 * 失敗：{ "success": false, "error": { "code": "VALIDATION_FAILED", "message": "...", "fieldErrors": [ ... ] } }
 * </pre>
 *
 * HTTP 狀態碼仍然照實回傳（400、404、500…），不是一律 200；
 * success 欄位讓前端不用看狀態碼也能快速判斷。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 成功但沒有資料要回傳（例如刪除、取消） */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
