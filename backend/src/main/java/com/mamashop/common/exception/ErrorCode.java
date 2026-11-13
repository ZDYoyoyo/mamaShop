package com.mamashop.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 錯誤代碼：每個代碼對應一個 HTTP 狀態碼與預設訊息。
 * 各模組的業務錯誤（例如庫存不足、訂單狀態不允許）在實作該功能的任務再新增。
 */
public enum ErrorCode {

    // ===== 通用 =====
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "輸入資料有誤"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "請求格式錯誤"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "請先登入"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "沒有權限執行此操作"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "找不到資料"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支援此請求方法"),
    CONFLICT(HttpStatus.CONFLICT, "資料已被其他人修改，請重新整理後再試"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生錯誤，請稍後再試"),

    // ===== 會員 =====
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "此 Email 已經註冊過"),
    // 不區分「帳號不存在」和「密碼錯誤」，避免被拿來試探哪些 Email 有註冊
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email 或密碼錯誤"),
    CANNOT_DISABLE_SELF(HttpStatus.CONFLICT, "不能停用自己的帳號"),

    // ===== 檔案上傳 =====
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "只接受 JPG、PNG、WebP 格式的圖片"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "檔案超過大小上限（10MB）"),

    // ===== 分類 =====
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "分類名稱已存在"),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT, "分類底下還有商品（包含下架的），請先把商品改到其他分類"),

    // ===== 商品 =====
    STOCK_ADJUSTMENT_INVALID(HttpStatus.CONFLICT, "調整後庫存不可小於 0"),

    // ===== 運送方式 =====
    LAST_SHIPPING_METHOD(HttpStatus.CONFLICT, "至少要保留一種啟用中的運送方式"),

    // ===== 訂單 =====
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT, "商品已下架或不存在"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "商品庫存不足"),
    SHIPPING_METHOD_UNAVAILABLE(HttpStatus.CONFLICT, "此運送方式目前無法使用"),
    ORDER_AMOUNT_TOO_LARGE(HttpStatus.BAD_REQUEST, "訂單金額超過上限"),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "訂單目前的狀態不允許此操作"),
    ORDER_CANNOT_CANCEL(HttpStatus.CONFLICT, "訂單已確認，無法自行取消，請聯絡賣家");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
