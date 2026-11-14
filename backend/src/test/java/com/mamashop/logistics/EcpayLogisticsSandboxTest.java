package com.mamashop.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.common.ecpay.EcpayCheckMacValue;
import com.mamashop.common.ecpay.EcpayCheckMacValue.Algorithm;
import com.mamashop.logistics.config.EcpayLogisticsProperties;
import com.mamashop.logistics.service.EcpayLogisticsGateway;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 真的連到綠界物流測試環境（P03）。會連外網、依賴綠界的測試環境，所以平常（含 CI）不跑。
 * 本機要跑：ECPAY_SANDBOX_TEST=true mvnw test -Dtest=EcpayLogisticsSandboxTest
 */
@EnabledIfEnvironmentVariable(named = "ECPAY_SANDBOX_TEST", matches = "true")
class EcpayLogisticsSandboxTest {

    private final EcpayLogisticsProperties properties = new EcpayLogisticsProperties(true, "2000933",
            "XBERn1YOvpM9nfZc", "h1ONHk4P4yqbl5LK", true, "https://logistics-stage.ecpay.com.tw",
            "https://shop.example.com", "王媽媽", "0911222333");
    private final EcpayLogisticsGateway gateway = new EcpayLogisticsGateway(properties);

    @Test
    void createShipmentOnSandbox_andVerifyResponseSignature() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MerchantID", "2000933");
        fields.put("MerchantTradeNo", "MSTEST" + System.currentTimeMillis() % 100000000);
        fields.put("MerchantTradeDate", DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                .format(LocalDateTime.now(ZoneId.of("Asia/Taipei"))));
        fields.put("LogisticsType", "CVS");
        fields.put("LogisticsSubType", "UNIMARTC2C");
        fields.put("GoodsAmount", "280");
        fields.put("IsCollection", "Y");
        fields.put("CollectionAmount", "280");
        fields.put("GoodsName", "mamaShop商品");
        fields.put("SenderName", "王媽媽");
        fields.put("SenderCellPhone", "0911222333");
        fields.put("ReceiverName", "王小明");
        fields.put("ReceiverCellPhone", "0912345678");
        fields.put("ReceiverStoreID", "131386");
        fields.put("ServerReplyURL", "https://shop.example.com/api/logistics/ecpay/notify");
        fields.put("CheckMacValue", EcpayCheckMacValue.compute(fields, properties.hashKey(), properties.hashIv(), Algorithm.MD5));

        String body = gateway.post("/Express/Create", fields);

        assertThat(body).startsWith("1|");
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.substring(2).split("&")) {
            String[] kv = pair.split("=", 2);
            result.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        assertThat(result.get("CVSPaymentNo")).isNotBlank();
        assertThat(EcpayCheckMacValue.verify(result, properties.hashKey(), properties.hashIv(), Algorithm.MD5)).isTrue();
    }
}
