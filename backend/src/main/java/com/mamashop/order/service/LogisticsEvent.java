package com.mamashop.order.service;

/**
 * 物流狀態對訂單的意義（P03）。綠界的狀態代碼由 logistics 模組翻譯成這幾種，訂單模組不需要知道綠界的代碼。
 */
public enum LogisticsEvent {
    /** 賣家已到門市寄件 */
    DROPPED_OFF,
    /** 包裹已送達取件門市（包含重新配達） */
    ARRIVED,
    /** 顧客已取件 */
    PICKED_UP,
    /** 顧客 7 天未取件，包裹退回中 */
    NOT_PICKED_UP,
    /** 包裹已退回寄件門市 */
    RETURNED_TO_SENDER
}
