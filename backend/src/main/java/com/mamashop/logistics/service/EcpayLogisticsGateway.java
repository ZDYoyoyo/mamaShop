package com.mamashop.logistics.service;

import com.mamashop.logistics.config.EcpayLogisticsProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 呼叫綠界物流 API 的 HTTP 那一層（P03）：送 form、拿回純文字。
 *
 * <p>獨立成一個類別，測試時可以換成假的（@MockitoBean），不用真的連到綠界；
 * 簽章、解析回應、判斷成功失敗都在 ShipmentService，可以完整測到。
 *
 * <p><b>一定要設逾時</b>：預設是無限等待。綠界很慢或沒回應時，管理員的請求會一直卡著，
 * 而且這段期間訂單是鎖住的（見 ShipmentService）。
 */
@Component
public class EcpayLogisticsGateway {

    private final RestClient restClient;

    /**
     * 直接用 RestClient.builder()：Spring Boot 4 把 RestClient.Builder 的自動設定拆到另一個模組（spring-boot-restclient），
     * 這裡只有一個地方呼叫外部 API，不另外加依賴
     */
    public EcpayLogisticsGateway(EcpayLogisticsProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().baseUrl(properties.apiUrl() == null ? "" : properties.apiUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** POST form（UTF-8）到綠界，回傳回應的內容（例如 1|AllPayLogisticsID=...） */
    public String post(String path, Map<String, String> fields) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach(form::add);
        byte[] body = restClient.post()
                .uri(path)
                .contentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8))
                .body(form)
                .retrieve()
                .body(byte[].class);
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
