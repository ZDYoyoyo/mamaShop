package com.mamashop.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mamashop.category.entity.Category;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.User;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * 驗證 Entity 對應：關聯、cascade、enum 存字串、樂觀鎖。
 * 連本機 PostgreSQL（replace = NONE），每個測試結束後自動 rollback，不會留下資料。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private TestEntityManager em;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setEmail("repo-test@example.com");
        customer.setPasswordHash("not-a-real-hash");
        customer.setName("測試顧客");
        em.persist(customer);

        Category category = new Category();
        category.setName("測試分類-repo");
        em.persist(category);

        product = new Product();
        product.setCategory(category);
        product.setName("測試商品");
        product.setPrice(new BigDecimal("320.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        em.persist(product);
    }

    @Test
    void saveOrder_cascadesItems_andStoresEnumsAsStrings() {
        Order order = newCvsOrder("TEST-0001");

        Order saved = orderRepository.save(order);
        em.flush();   // 真的送出 INSERT（會經過資料庫的 CHECK 限制）
        em.clear();   // 清掉一級快取，下面的查詢一定從資料庫讀

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getProductName()).isEqualTo("測試商品");
        assertThat(found.getTotalAmount()).isEqualByComparingTo("700.00");
        assertThat(found.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();

        // EnumType.STRING：資料庫存的是名稱字串，不是序號
        Object rawStatus = em.getEntityManager()
                .createNativeQuery("SELECT status FROM orders WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(rawStatus).isEqualTo("PENDING_CONFIRM");
    }

    @Test
    void saveStatusHistory_withOperator() {
        Order order = orderRepository.save(newCvsOrder("TEST-0002"));
        historyRepository.save(new OrderStatusHistory(order, null, OrderStatus.PENDING_CONFIRM, customer, "建立訂單"));
        em.flush();
        em.clear();

        OrderStatusHistory history = historyRepository.findAll().stream()
                .filter(h -> h.getOrder().getId().equals(order.getId()))
                .findFirst().orElseThrow();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(history.getOperator().getId()).isEqualTo(customer.getId());
        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    void updateWithStaleVersion_throwsOptimisticLockException() {
        Long id = orderRepository.save(newCvsOrder("TEST-0003")).getId();
        em.flush();
        em.clear();

        // 管理員 A 打開訂單（version = 0）
        Order seenByAdminA = em.find(Order.class, id);
        em.detach(seenByAdminA);

        // 管理員 B 也打開同一張訂單，先按下「確認訂單」（version 變成 1）
        Order seenByAdminB = em.find(Order.class, id);
        seenByAdminB.setStatus(OrderStatus.PROCESSING);
        em.flush();
        em.clear();
        assertThat(em.find(Order.class, id).getVersion()).isEqualTo(1);
        em.clear();

        // 管理員 A 拿著舊資料（version = 0）送出取消 → 被擋下，不會蓋掉 B 的修改
        seenByAdminA.setStatus(OrderStatus.CANCELLED);
        assertThrows(OptimisticLockException.class, () -> {
            em.merge(seenByAdminA);
            em.flush();
        });
    }

    private Order newCvsOrder(String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUser(customer);
        order.setRecipientName("王小明");
        order.setRecipientPhone("0912345678");
        order.setShippingMethod(ShippingMethodCode.CVS_PICKUP);
        order.setCvsBrand(CvsBrand.SEVEN);
        order.setCvsStoreCode("123456");
        order.setCvsStoreName("測試門市");
        order.setStatus(OrderStatus.PENDING_CONFIRM);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("640.00"));
        order.addItem(item);

        order.setItemsAmount(new BigDecimal("640.00"));
        order.setShippingFee(new BigDecimal("60.00"));
        order.setTotalAmount(new BigDecimal("700.00"));
        return order;
    }
}
