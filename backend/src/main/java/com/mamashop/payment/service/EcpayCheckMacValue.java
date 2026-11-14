package com.mamashop.payment.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 綠界的檢查碼（CheckMacValue）：證明參數是「知道 HashKey／HashIV 的人」送的、而且沒被改過。
 *
 * <p>送去綠界（建立交易）和綠界送來（付款通知）都用同一個算法：
 * <ol>
 *   <li>參數依名稱排序（不分大小寫），排除 CheckMacValue 本身</li>
 *   <li>組成 {@code HashKey=...&a=1&b=2&HashIV=...}</li>
 *   <li>URL encode（和 .NET 的 HttpUtility.UrlEncode 一樣），轉小寫</li>
 *   <li>SHA-256，轉大寫十六進位</li>
 * </ol>
 * 綠界文件的範例在 EcpayCheckMacValueTest，並用綠界測試環境實際驗證過（簽錯會回「CheckMacValue Error」）。
 */
public final class EcpayCheckMacValue {

    public static final String FIELD = "CheckMacValue";

    private EcpayCheckMacValue() {
    }

    public static String compute(Map<String, String> params, String hashKey, String hashIv) {
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        params.forEach((name, value) -> {
            if (!FIELD.equalsIgnoreCase(name)) {
                sorted.put(name, value == null ? "" : value);
            }
        });
        StringBuilder raw = new StringBuilder("HashKey=").append(hashKey);
        sorted.forEach((name, value) -> raw.append('&').append(name).append('=').append(value));
        raw.append("&HashIV=").append(hashIv);
        return sha256Upper(dotNetUrlEncode(raw.toString()).toLowerCase(Locale.ROOT));
    }

    /**
     * 檢查綠界送來的檢查碼。用 MessageDigest.isEqual（比較時間固定）而不是 equals：
     * equals 遇到第一個不同的字元就返回，理論上可以從回應時間一個字一個字猜出正確的值（timing attack）
     */
    public static boolean verify(Map<String, String> params, String hashKey, String hashIv) {
        String received = params.get(FIELD);
        if (received == null) {
            return false;
        }
        String expected = compute(params, hashKey, hashIv);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Java 的 URLEncoder 和 .NET 的 UrlEncode 只差在這幾個字元：.NET 不編碼 ! ( ) *，Java 會編碼 ! ( ) 但不編碼 *。
     * 先用 Java 編碼，再把 ! ( ) 換回來（* 兩邊都不編碼）
     */
    private static String dotNetUrlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("%21", "!")
                .replace("%28", "(")
                .replace("%29", ")");
    }

    private static String sha256Upper(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
