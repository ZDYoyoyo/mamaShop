package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 併發測試：很多人同時搶購時，庫存不會被扣成負數，也不會賣超過庫存。
 *
 * <p>做法：用 CountDownLatch 讓所有執行緒「準備好之後同一瞬間」一起呼叫 createOrder，
 * 盡量製造真正同時搶同一列的情況。
 */
@SpringBootTest
class OrderConcurrencyTest {

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private OrderTestData data;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void stock10_50ConcurrentBuyers_exactly10Succeed() throws Exception {
        Product product = data.product("限量包", "500.00", 10, ProductStatus.ON_SALE);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> {
                orderService.createOrder(data.user().getId(), request(product.getId()));
                return null;
            });
        }
        Result result = runConcurrently(tasks);

        assertThat(result.succeeded).isEqualTo(10);
        assertThat(result.insufficientStock).isEqualTo(40);
        assertThat(result.unexpected).isEmpty();
        assertThat(data.stockOf(product)).isZero();
        assertThat(data.orderCount()).isEqualTo(10);
    }

    @Test
    void oppositeItemOrder_doesNotDeadlock() throws Exception {
        // 一半的訂單是「X、Y」，另一半是「Y、X」。沒有依 id 排序的話，這種情況容易死結
        Product x = data.product("商品X", "100.00", 1000, ProductStatus.ON_SALE);
        Product y = data.product("商品Y", "100.00", 1000, ProductStatus.ON_SALE);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Long first = i % 2 == 0 ? x.getId() : y.getId();
            Long second = i % 2 == 0 ? y.getId() : x.getId();
            tasks.add(() -> {
                orderService.createOrder(data.user().getId(), request(first, second));
                return null;
            });
        }
        Result result = runConcurrently(tasks);

        assertThat(result.unexpected).isEmpty();      // 沒有任何死結錯誤
        assertThat(result.succeeded).isEqualTo(40);
        assertThat(data.stockOf(x)).isEqualTo(960);
        assertThat(data.stockOf(y)).isEqualTo(960);
    }

    // ===== helpers =====

    private static CreateOrderRequest request(Long... productIds) {
        List<CreateOrderRequest.Item> items = new ArrayList<>();
        for (Long id : productIds) {
            items.add(new CreateOrderRequest.Item(id, 1));
        }
        return new CreateOrderRequest(items, ShippingMethodCode.HOME_DELIVERY,
                "王小明", "0912345678", "台北市", null, null, null, null);
    }

    private static Result runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();          // 所有執行緒在這裡等，同時出發
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();

            Result result = new Result();
            for (Future<Void> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    result.succeeded++;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof BusinessException be
                            && be.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
                        result.insufficientStock++;
                    } else {
                        result.unexpected.add(e.getCause());
                    }
                }
            }
            return result;
        } finally {
            pool.shutdownNow();
        }
    }

    private static class Result {
        int succeeded;
        int insufficientStock;
        List<Throwable> unexpected = new ArrayList<>();
    }
}
