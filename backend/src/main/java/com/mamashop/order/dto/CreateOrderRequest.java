package com.mamashop.order.dto;

import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 建立訂單（結帳）。
 *
 * <p>請求裡只有「商品 id＋數量」，沒有價格、沒有金額：
 * 價格一律以伺服器當下的資料為準，前端傳來的金額不可信（可以被竄改）。
 *
 * <p>{@link ValidShippingInfo}：依運送方式檢查必填欄位（宅配要地址、超商要門市；
 * 超商取貨的收件人姓名還要符合超商的格式，P03），
 * 這種「欄位之間互相影響」的規則無法只用單一欄位的註解表達，所以寫成類別層級的驗證。
 */
@ValidShippingInfo
public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) @Valid
        List<Item> items,

        @NotNull
        ShippingMethodCode shippingMethod,

        @NotBlank @Size(max = 50)
        String recipientName,

        @NotBlank
        @Pattern(regexp = "^09\\d{8}$", message = "需為 09 開頭的 10 碼手機號碼")
        String recipientPhone,

        /* 宅配必填 */
        @Size(max = 255)
        String shippingAddress,

        /* 超商取貨必填。門市由綠界電子地圖帶入（P02），沒開電子地圖時顧客手動填寫；兩種來源後端都用同一套規則檢查 */
        CvsBrand cvsBrand,
        @Size(max = 20) String cvsStoreCode,
        @Size(max = 50) String cvsStoreName,

        @Size(max = 500)
        String note,

        /* 付款方式（P01）。沒有傳 = 貨到付款（舊版前端相容） */
        PaymentMethod paymentMethod) {

    /** 沒有指定付款方式（貨到付款） */
    public CreateOrderRequest(List<Item> items, ShippingMethodCode shippingMethod, String recipientName,
                              String recipientPhone, String shippingAddress, CvsBrand cvsBrand,
                              String cvsStoreCode, String cvsStoreName, String note) {
        this(items, shippingMethod, recipientName, recipientPhone, shippingAddress, cvsBrand,
                cvsStoreCode, cvsStoreName, note, null);
    }

    public CreateOrderRequest {
        recipientName = trim(recipientName);
        recipientPhone = trim(recipientPhone);
        shippingAddress = trim(shippingAddress);
        cvsStoreCode = trim(cvsStoreCode);
        cvsStoreName = trim(cvsStoreName);
        note = trim(note);
        paymentMethod = paymentMethod == null ? PaymentMethod.COD : paymentMethod;
    }

    /** 購物車的一個品項 */
    public record Item(
            @NotNull @Positive Long productId,
            @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
