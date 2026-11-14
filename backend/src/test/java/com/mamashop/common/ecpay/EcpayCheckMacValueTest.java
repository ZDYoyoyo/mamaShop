package com.mamashop.common.ecpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 綠界檢查碼（P01）。測試值是綠界文件上的範例（公開的測試商店） */
public class EcpayCheckMacValueTest {

    public static final String HASH_KEY = "pwFHCqoQZGmho4w6";
    public static final String HASH_IV = "EkRm7iFT261dpevs";

    @Test
    void matchesEcpayDocumentExample() {
        Map<String, String> params = new HashMap<>();
        params.put("ChoosePayment", "ALL");
        params.put("EncryptType", "1");
        params.put("ItemName", "Apple iphone 15");
        params.put("MerchantID", "3002607");
        params.put("MerchantTradeDate", "2023/03/12 15:30:23");
        params.put("MerchantTradeNo", "ecpay20230312153023");
        params.put("PaymentType", "aio");
        params.put("ReturnURL", "https://www.ecpay.com.tw/receive.php");
        params.put("TotalAmount", "30000");
        params.put("TradeDesc", "促銷方案");

        assertThat(EcpayCheckMacValue.compute(params, HASH_KEY, HASH_IV))
                .isEqualTo("6C51C9E6888DE861FD62FB1DD17029FC742634498FD813DC43D4243B5685B840");
    }

    @Test
    void verify_acceptsCorrect_rejectsTampered() {
        Map<String, String> params = new HashMap<>(Map.of(
                "MerchantID", "3002607", "MerchantTradeNo", "M26111400000000101",
                "RtnCode", "1", "TradeAmt", "890", "RtnMsg", "交易成功 (測試) *-_.!()"));
        params.put("CheckMacValue", EcpayCheckMacValue.compute(params, HASH_KEY, HASH_IV));
        assertThat(EcpayCheckMacValue.verify(params, HASH_KEY, HASH_IV)).isTrue();

        // 綠界回傳的檢查碼大小寫不影響
        params.put("CheckMacValue", params.get("CheckMacValue").toLowerCase());
        assertThat(EcpayCheckMacValue.verify(params, HASH_KEY, HASH_IV)).isTrue();

        // 改了金額（例如有人想用 1 元付 890 元的訂單）→ 不通過
        params.put("TradeAmt", "1");
        assertThat(EcpayCheckMacValue.verify(params, HASH_KEY, HASH_IV)).isFalse();

        // 沒有檢查碼、用錯的 HashKey → 不通過
        params.put("TradeAmt", "890");
        assertThat(EcpayCheckMacValue.verify(params, "wrong-key", HASH_IV)).isFalse();
        params.remove("CheckMacValue");
        assertThat(EcpayCheckMacValue.verify(params, HASH_KEY, HASH_IV)).isFalse();
    }

    /**
     * 物流（P03）用 MD5：這是綠界測試環境「建立物流訂單」實際回傳的內容（店到店測試商店的 HashKey／HashIV），
     * 回傳裡的檢查碼要能用同樣的算法驗證，才知道回應真的是綠界送的。
     */
    @Test
    void md5_verifiesRealLogisticsResponseFromEcpaySandbox() {
        String response = "AllPayLogisticsID=3799461&BookingNote=&CheckMacValue=27D6A83E0B69FE79AA76AAAEFB332E38"
                + "&CVSPaymentNo=D8858181&CVSValidationNo=5655&GoodsAmount=280&LogisticsSubType=UNIMARTC2C"
                + "&LogisticsType=CVS&MerchantID=2000933&MerchantTradeNo=MSL8532105549&ReceiverAddress="
                + "&ReceiverCellPhone=0912345678&ReceiverEmail=&ReceiverName=王小明&ReceiverPhone=&RtnCode=300"
                + "&RtnMsg=訂單處理中(綠界已收到訂單資料)&UpdateStatusDate=2026/11/14 15:18:11";
        Map<String, String> params = new HashMap<>();
        for (String pair : response.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], kv[1]);
        }

        assertThat(EcpayCheckMacValue.verify(params, "XBERn1YOvpM9nfZc", "h1ONHk4P4yqbl5LK",
                EcpayCheckMacValue.Algorithm.MD5)).isTrue();
        // 同樣的資料用 SHA-256 驗證不會過（金流和物流的算法不能搞混）
        assertThat(EcpayCheckMacValue.verify(params, "XBERn1YOvpM9nfZc", "h1ONHk4P4yqbl5LK")).isFalse();
    }
}
