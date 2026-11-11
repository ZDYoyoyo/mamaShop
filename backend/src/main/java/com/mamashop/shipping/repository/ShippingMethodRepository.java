package com.mamashop.shipping.repository;

import com.mamashop.shipping.entity.ShippingMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {

    /** 前台只顯示啟用中的運送方式 */
    List<ShippingMethod> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
