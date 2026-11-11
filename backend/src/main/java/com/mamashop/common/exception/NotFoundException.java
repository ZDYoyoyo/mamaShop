package com.mamashop.common.exception;

/**
 * 找不到資料（404）。例如：throw new NotFoundException("找不到商品");
 *
 * <p>權限檢查也可以用它：顧客查詢別人的訂單時回 404 而不是 403，
 * 避免讓人知道「這個訂單編號存在」。
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
