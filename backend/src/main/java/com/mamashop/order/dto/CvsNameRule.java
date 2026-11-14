package com.mamashop.order.dto;

import java.util.regex.Pattern;

/**
 * 超商取貨的姓名規則（P03）：取件時要核對證件，綠界也會拒絕不符合的收件人／寄件人姓名。
 *
 * <p>規則是用綠界測試環境實測出來的（文件只寫「4～10 字元」）：
 * 中文算 2、英文字母和空白算 1，總共 4～10；不能有數字和符號。
 * 「王小明」「王 小明」「Johnny」「John Smith」「王Amy」可以；「林」「Tom」「王小明2」「歐陽美麗娜子」不行。
 */
public final class CvsNameRule {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{IsHan}A-Za-z ]+$");

    private CvsNameRule() {
    }

    public static boolean isValid(String name) {
        if (name == null || !ALLOWED.matcher(name).matches()) {
            return false;
        }
        int width = name.codePoints().map(c -> c <= 0x7F ? 1 : 2).sum();
        return width >= 4 && width <= 10;
    }
}
