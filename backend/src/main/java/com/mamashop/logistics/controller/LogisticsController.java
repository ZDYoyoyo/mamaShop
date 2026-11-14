package com.mamashop.logistics.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.logistics.dto.CvsMapFormResponse;
import com.mamashop.logistics.dto.LogisticsOptionsResponse;
import com.mamashop.logistics.service.CvsMapService;
import com.mamashop.order.entity.CvsBrand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 超商電子地圖選門市（P02） */
@Tag(name = "物流", description = "綠界超商電子地圖")
@RestController
public class LogisticsController {

    private final CvsMapService cvsMapService;

    public LogisticsController(CvsMapService cvsMapService) {
        this.cvsMapService = cvsMapService;
    }

    /** 結帳頁用：超商門市要用電子地圖選，還是手動填（不用登入） */
    @Operation(summary = "物流設定")
    @GetMapping("/api/logistics/options")
    public ApiResponse<LogisticsOptionsResponse> options() {
        return ApiResponse.ok(cvsMapService.options());
    }

    /** 前往電子地圖的表單（結帳頁按「選擇門市」） */
    @Operation(summary = "前往超商電子地圖")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/api/logistics/cvs-map")
    public ApiResponse<CvsMapFormResponse> cvsMap(@RequestParam CvsBrand brand,
                                                  @RequestParam(defaultValue = "true") boolean collection,
                                                  @RequestParam(defaultValue = "false") boolean mobile) {
        return ApiResponse.ok(cvsMapService.mapForm(brand, collection, mobile));
    }

    /**
     * 電子地圖選好門市後，顧客的瀏覽器 POST 回來（ServerReplyURL）。不用登入（綠界的頁面不會帶我們的 JWT）。
     * 回 303 See Other：瀏覽器改用 GET 打開結帳頁（302 有些瀏覽器會沿用 POST）
     */
    @Operation(summary = "電子地圖選好門市（給綠界的地圖頁呼叫）")
    @PostMapping(path = "/api/logistics/ecpay/cvs-map-reply", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> cvsMapReply(@RequestParam Map<String, String> params) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(cvsMapService.handleMapReply(params)).build();
    }
}
