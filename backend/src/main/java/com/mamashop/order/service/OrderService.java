package com.mamashop.order.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.repository.OrderStatusHistoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.shipping.repository.ShippingMethodRepository;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 訂單（顧客端）。
 *
 * <h2>建立訂單的流程（全部在同一個資料庫交易內）</h2>
 * <ol>
 *   <li>合併重複商品、依商品 id 排序</li>
 *   <li>確認運送方式可用，取得當下運費</li>
 *   <li><b>逐一條件更新扣庫存</b>：任何一個失敗就丟出例外 → 整筆交易回滾，前面扣掉的也會還原</li>
 *   <li>讀取商品（此時這些列已被本交易鎖住），把名稱、單價快照到訂單明細</li>
 *   <li>計算金額、產生訂單編號、寫入訂單與第一筆狀態紀錄</li>
 * </ol>
 */
@Service
public class OrderService {

    /** 同一個商品合併後的最大數量（避免一次買光或惡意大量下單） */
    private static final int MAX_QUANTITY_PER_PRODUCT = 99;
    /** orders.total_amount 是 NUMERIC(10,2)，超過會在寫入時出錯，先擋下 */
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("99999999.99");

    private final ProductRepository productRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(ProductRepository productRepository,
                        ShippingMethodRepository shippingMethodRepository,
                        OrderRepository orderRepository,
                        OrderStatusHistoryRepository historyRepository,
                        UserRepository userRepository,
                        OrderNumberGenerator orderNumberGenerator) {
        this.productRepository = productRepository;
        this.shippingMethodRepository = shippingMethodRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    /**
     * 建立訂單。
     *
     * <p>@Transactional：這個方法裡所有 SQL 在同一個交易。方法正常結束 → COMMIT；
     * 丟出 RuntimeException（例如 BusinessException）→ ROLLBACK，扣掉的庫存、寫到一半的訂單全部還原。
     *
     * @param userId 下單的會員，一律從 JWT 取得（Controller 的 @AuthenticationPrincipal），不接受前端傳入
     */
    @Transactional
    public OrderDetailResponse createOrder(Long userId, CreateOrderRequest request) {
        // 1. 合併重複商品並依 id 排序（TreeMap 會自動排序，排序的原因見 deductStock）
        SortedMap<Long, Integer> quantities = mergeQuantities(request);

        // 2. 運送方式與運費（停用中的不能用）
        ShippingMethod shippingMethod = shippingMethodRepository.findByCode(request.shippingMethod())
                .filter(ShippingMethod::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPPING_METHOD_UNAVAILABLE));

        // 3. 扣庫存（任何一個失敗 → 例外 → 整筆回滾）
        deductStock(quantities);

        // 4. 讀取商品做快照。扣庫存的 UPDATE 已經鎖住這些列，交易結束前別人改不了價格或狀態，
        //    所以這裡讀到的價格，就是和扣庫存同一個時間點的價格
        Map<Long, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 5. 建立訂單
        Order order = new Order();
        BigDecimal itemsAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            int quantity = entry.getValue();
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());   // 名稱快照：之後商品改名不影響這筆訂單
            item.setUnitPrice(product.getPrice());    // 價格快照：之後商品改價不影響這筆訂單
            item.setQuantity(quantity);
            item.setSubtotal(subtotal);
            order.addItem(item);

            itemsAmount = itemsAmount.add(subtotal);
        }

        BigDecimal shippingFee = shippingMethod.getFee(); // 運費快照
        BigDecimal totalAmount = itemsAmount.add(shippingFee);
        if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.ORDER_AMOUNT_TOO_LARGE);
        }

        // getReferenceById：只建立代理物件、不查詢資料庫（我們只需要 user_id 這個外鍵）
        User customer = userRepository.getReferenceById(userId);

        order.setOrderNo(orderNumberGenerator.generate());
        order.setUser(customer);
        order.setItemsAmount(itemsAmount);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(totalAmount);
        order.setRecipientName(request.recipientName());
        order.setRecipientPhone(request.recipientPhone());
        order.setNote(request.note());
        applyShippingInfo(order, request);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setStatus(OrderStatus.PENDING_CONFIRM); // 貨到付款：先待家人確認
        orderRepository.save(order); // cascade 會一起 INSERT 所有明細

        historyRepository.save(new OrderStatusHistory(order, null, OrderStatus.PENDING_CONFIRM, customer, "顧客下單"));

        return OrderDetailResponse.from(order);
    }

    /**
     * 同一個商品在購物車出現兩次（例如前端 bug），合併成一筆。
     * order_items 有 UNIQUE (order_id, product_id)，不合併的話寫入時會失敗。
     */
    private SortedMap<Long, Integer> mergeQuantities(CreateOrderRequest request) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (CreateOrderRequest.Item item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        quantities.forEach((productId, quantity) -> {
            if (quantity > MAX_QUANTITY_PER_PRODUCT) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "單一商品數量不可超過 " + MAX_QUANTITY_PER_PRODUCT + " 件");
            }
        });
        return quantities;
    }

    /**
     * 依商品 id「由小到大」逐一扣庫存。
     *
     * <p>為什麼要排序：避免死結（deadlock）。
     * 假設訂單 A 買「商品1、商品2」，訂單 B 買「商品2、商品1」，兩者同時執行：
     * A 鎖住商品1、B 鎖住商品2，接著 A 等商品2、B 等商品1 → 互相等待，永遠卡住
     * （PostgreSQL 偵測到後會讓其中一筆失敗）。
     * 大家都照同樣順序上鎖，就不可能形成這種循環等待。
     */
    private void deductStock(SortedMap<Long, Integer> quantities) {
        Instant now = Instant.now();
        quantities.forEach((productId, quantity) -> {
            int updated = productRepository.decreaseStock(productId, quantity, ProductStatus.ON_SALE, now);
            if (updated == 0) {
                throw stockFailure(productId);
            }
        });
    }

    /** 扣庫存失敗時，查出原因給使用者清楚的訊息 */
    private BusinessException stockFailure(Long productId) {
        return productRepository.findById(productId)
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE)
                .map(p -> new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "「" + p.getName() + "」庫存不足，目前剩 " + p.getStock() + " 件"))
                .orElseGet(() -> new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE));
    }

    /** 依運送方式只保留對應的欄位（宅配不存門市、超商不存地址） */
    private void applyShippingInfo(Order order, CreateOrderRequest request) {
        order.setShippingMethod(request.shippingMethod());
        if (request.shippingMethod() == ShippingMethodCode.HOME_DELIVERY) {
            order.setShippingAddress(request.shippingAddress());
        } else {
            order.setCvsBrand(request.cvsBrand());
            order.setCvsStoreCode(request.cvsStoreCode());
            order.setCvsStoreName(request.cvsStoreName());
        }
    }
}
