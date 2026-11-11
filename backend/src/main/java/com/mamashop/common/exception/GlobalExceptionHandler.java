package com.mamashop.common.exception;

import com.mamashop.common.response.ApiError;
import com.mamashop.common.response.ApiError.FieldErrorDetail;
import com.mamashop.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全域例外處理：把所有 Controller 丟出的例外轉成統一的 {@link ApiResponse} 格式。
 *
 * <p>原則：
 * <ul>
 *   <li>4xx（使用者的錯）：回傳清楚的訊息，log 只記 warn/debug，不印堆疊</li>
 *   <li>5xx（系統的錯）：對外只說「系統發生錯誤」，不洩漏例外內容；完整堆疊寫進 log 方便排查</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 業務錯誤：自己丟出的 BusinessException（含 NotFoundException） */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("Business exception: {} {}", ex.getErrorCode(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage());
    }

    /** @Valid @RequestBody 驗證失敗，例如註冊時 email 格式錯誤 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return buildValidation(fieldErrors);
    }

    /**
     * Controller 方法參數上的驗證失敗，例如 @RequestParam @Min(1) int page。
     * （Spring 6.1 起內建方法參數驗證，Controller 類別不用再加 @Validated）
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                // 物件型參數（@Valid 的 DTO）：逐欄位列出
                errors.getFieldErrors().forEach(fe -> fieldErrors.add(toDetail(fe)));
            } else {
                // 單一值參數（@RequestParam、@PathVariable）：用參數名稱當欄位
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(err ->
                        fieldErrors.add(new FieldErrorDetail(name, err.getDefaultMessage())));
            }
        });
        return buildValidation(fieldErrors);
    }

    /** Service 層用 @Validated 驗證失敗時丟出 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(lastPathNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return buildValidation(fieldErrors);
    }

    /** JSON 格式錯誤、型別不符（例如 quantity 傳了 "abc"） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return build(ErrorCode.BAD_REQUEST, "請求內容格式錯誤");
    }

    /** 路徑或查詢參數型別不符，例如 /api/products/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildValidation(List.of(new FieldErrorDetail(ex.getName(), "格式不正確")));
    }

    /** 缺少必要的查詢參數 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildValidation(List.of(new FieldErrorDetail(ex.getParameterName(), "為必填")));
    }

    /** 沒有對應的 API 路徑 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "找不到此 API");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
    }

    /** 樂觀鎖衝突：兩位管理員同時修改同一筆資料，後送出的那位會收到這個錯誤 */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.info("Optimistic lock conflict: {}", ex.getMessage());
        return build(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getDefaultMessage());
    }

    /** 其他所有例外 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // Spring MVC 的其他 4xx 例外（例如 415 不支援的 Content-Type）：保留原本的狀態碼
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.debug("Client error {}: {}", status.value(), ex.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail(new ApiError(ErrorCode.BAD_REQUEST.name(), ErrorCode.BAD_REQUEST.getDefaultMessage())));
        }
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    // ===== helpers =====

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(new ApiError(code.name(), message)));
    }

    private ResponseEntity<ApiResponse<Void>> buildValidation(List<FieldErrorDetail> fieldErrors) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(new ApiError(code.name(), code.getDefaultMessage(), fieldErrors)));
    }

    private FieldErrorDetail toDetail(FieldError fe) {
        return new FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
    }

    /** "createOrder.request.quantity" → "quantity" */
    private String lastPathNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
