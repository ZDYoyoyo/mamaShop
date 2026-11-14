package com.mamashop.logistics.dto;

import java.util.Map;

/** 列印寄件單的表單：後台用 form POST 到綠界（開新分頁），綠界回傳可以列印的寄件單頁面 */
public record LabelFormResponse(String action, Map<String, String> fields) {
}
