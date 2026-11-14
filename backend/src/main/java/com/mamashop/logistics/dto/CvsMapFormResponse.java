package com.mamashop.logistics.dto;

import java.util.Map;

/** 前往綠界電子地圖的表單：前端用這些欄位組一個 form，POST 到 action（整頁跳到地圖） */
public record CvsMapFormResponse(String action, Map<String, String> fields) {
}
