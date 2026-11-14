package com.mamashop.payment.dto;

import java.util.Map;

/**
 * 前往綠界付款頁需要的資料：前端用這些欄位組一個 form，POST 到 action（整個頁面跳到綠界）。
 * 欄位已經算好檢查碼，前端不能改（改了綠界會拒絕）。
 */
public record PaymentFormResponse(String action, Map<String, String> fields) {
}
