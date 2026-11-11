package com.mamashop.shipping.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.shipping.dto.AdminShippingMethodResponse;
import com.mamashop.shipping.dto.ShippingMethodResponse;
import com.mamashop.shipping.dto.ShippingMethodUpdateRequest;
import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.repository.ShippingMethodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingMethodService {

    private final ShippingMethodRepository shippingMethodRepository;

    public ShippingMethodService(ShippingMethodRepository shippingMethodRepository) {
        this.shippingMethodRepository = shippingMethodRepository;
    }

    // ===== 前台 =====

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> listEnabledMethods() {
        return shippingMethodRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(ShippingMethodResponse::from)
                .toList();
    }

    // ===== 後台 =====

    @Transactional(readOnly = true)
    public List<AdminShippingMethodResponse> listForAdmin() {
        return shippingMethodRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AdminShippingMethodResponse::from)
                .toList();
    }

    /** 修改名稱、運費、啟用狀態。改運費只影響之後的新訂單（訂單會保存下單當下的運費）。 */
    @Transactional
    public AdminShippingMethodResponse update(Long id, ShippingMethodUpdateRequest request) {
        ShippingMethod method = shippingMethodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("找不到運送方式"));

        boolean disabling = method.isEnabled() && !request.enabled();
        if (disabling && shippingMethodRepository.countByEnabledTrue() <= 1) {
            // 全部停用的話，顧客就無法結帳
            throw new BusinessException(ErrorCode.LAST_SHIPPING_METHOD);
        }

        method.setName(request.name());
        method.setFee(request.fee());
        method.setEnabled(request.enabled());
        method.setSortOrder(request.sortOrder());
        return AdminShippingMethodResponse.from(shippingMethodRepository.saveAndFlush(method));
    }
}
