package com.mamashop.shipping.repository;

import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {

    /** 前台只顯示啟用中的運送方式 */
    List<ShippingMethod> findByEnabledTrueOrderBySortOrderAscIdAsc();

    /** 後台：全部 */
    List<ShippingMethod> findAllByOrderBySortOrderAscIdAsc();

    long countByEnabledTrue();

    Optional<ShippingMethod> findByCode(ShippingMethodCode code);
}
