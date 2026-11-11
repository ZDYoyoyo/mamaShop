package com.mamashop.common.exception;

/**
 * 可預期的業務錯誤（例如庫存不足、狀態不允許），由 {@link GlobalExceptionHandler} 轉成統一的錯誤回應。
 * 繼承 RuntimeException：不用在每個方法簽章上 throws，而且在 @Transactional 方法中丟出時會自動 rollback。
 *
 * <p>message 會直接顯示給使用者，不要放入內部資訊（SQL、堆疊、其他會員的資料）。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
