package com.mamashop.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.order.entity.CvsBrand;
import com.mamashop.shipping.entity.ShippingMethodCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 下單的運送資訊檢查（P03 新增：超商取貨的收件人姓名格式、OK 超商停止服務） */
class ShippingInfoValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void cvsPickup_recipientNameMustFitEcpayRule() {
        assertThat(errors(cvs("王小明", CvsBrand.SEVEN))).isEmpty();
        assertThat(errors(cvs("John Smith", CvsBrand.FAMILY))).isEmpty();

        assertThat(errors(cvs("林", CvsBrand.SEVEN))).containsEntry("recipientName", ShippingInfoValidator.CVS_NAME_MESSAGE);
        assertThat(errors(cvs("王小明2", CvsBrand.SEVEN))).containsKey("recipientName");
    }

    @Test
    void homeDelivery_doesNotRestrictName() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(1L, 1)),
                ShippingMethodCode.HOME_DELIVERY, "林", "0912345678", "台北市", null, null, null, null);
        assertThat(errors(request)).isEmpty();
    }

    @Test
    void okMart_isRejectedForNewOrders() {
        assertThat(errors(cvs("王小明", CvsBrand.OK))).containsEntry("cvsBrand", "OK 超商店到店已停止服務，請選擇其他超商");
    }

    private static CreateOrderRequest cvs(String name, CvsBrand brand) {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(1L, 1)), ShippingMethodCode.CVS_PICKUP,
                name, "0912345678", null, brand, "131386", "測試門市", null);
    }

    private Map<String, String> errors(CreateOrderRequest request) {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        return violations.stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a));
    }
}
