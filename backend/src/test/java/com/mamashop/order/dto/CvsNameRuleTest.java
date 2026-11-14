package com.mamashop.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 超商取貨的姓名規則（P03）：每個例子都在綠界測試環境實際送過，結果和綠界一致 */
class CvsNameRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {"王小明", "王 小明", "Johnny", "John Smith", "王Amy", "歐陽娜娜"})
    void acceptedByEcpay(String name) {
        assertThat(CvsNameRule.isValid(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"林", "Tom", "歐陽美麗娜子", "JohnnySmith", "王小明2", "王.小明", ""})
    void rejectedByEcpay(String name) {
        // 林：1 個中文；Tom：3 個英文；歐陽美麗娜子：6 個中文；JohnnySmith：11 個英文；王小明2：有數字
        assertThat(CvsNameRule.isValid(name)).isFalse();
    }
}
