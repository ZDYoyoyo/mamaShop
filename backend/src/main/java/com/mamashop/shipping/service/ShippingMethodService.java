package com.mamashop.shipping.service;

import com.mamashop.shipping.dto.ShippingMethodResponse;
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

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> listEnabledMethods() {
        return shippingMethodRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(ShippingMethodResponse::from)
                .toList();
    }
}
