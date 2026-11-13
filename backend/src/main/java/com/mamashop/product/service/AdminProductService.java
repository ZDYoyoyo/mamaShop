package com.mamashop.product.service;

import static com.mamashop.product.repository.ProductSpecifications.hasStatus;
import static com.mamashop.product.repository.ProductSpecifications.inCategory;
import static com.mamashop.product.repository.ProductSpecifications.nameContains;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.common.response.PageResponse;
import com.mamashop.common.storage.ImageStorageService;
import com.mamashop.product.cache.ProductCacheEvictor;
import com.mamashop.product.dto.AdminProductResponse;
import com.mamashop.product.dto.ProductCreateRequest;
import com.mamashop.product.dto.ProductUpdateRequest;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 後台商品管理：看得到所有狀態的商品。前台查詢在 {@link ProductService}。 */
@Service
public class AdminProductService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final String IMAGE_SUB_DIR = "products";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ImageStorageService imageStorageService;
    private final ProductCacheEvictor productCacheEvictor;

    public AdminProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                               ImageStorageService imageStorageService, ProductCacheEvictor productCacheEvictor) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageStorageService = imageStorageService;
        this.productCacheEvictor = productCacheEvictor;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> search(Long categoryId, String keyword, ProductStatus status,
                                                     int page, int size) {
        Specification<Product> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        if (categoryId != null) {
            spec = spec.and(inCategory(categoryId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(nameContains(keyword.trim()));
        }
        return PageResponse.of(
                productRepository.findAll(spec, PageRequest.of(page - 1, size, NEWEST_FIRST)),
                AdminProductResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return AdminProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public AdminProductResponse create(ProductCreateRequest request) {
        Product product = new Product();
        product.setCategory(findCategory(request.categoryId()));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setStatus(request.status() == null ? ProductStatus.OFF_SALE : request.status());
        Product saved = productRepository.saveAndFlush(product);
        // 新增時直接上架的話，前台列表要出現它（下架的新商品清了也無妨）
        productCacheEvictor.evictProduct(saved.getId());
        return AdminProductResponse.from(saved);
    }

    @Transactional
    public AdminProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findOrThrow(id);
        product.setCategory(findCategory(request.categoryId()));
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        // 改價格不影響已成立的訂單：訂單明細存的是下單當下的價格快照
        productCacheEvictor.evictProduct(id);
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public AdminProductResponse changeStatus(Long id, ProductStatus status) {
        Product product = findOrThrow(id);
        product.setStatus(status);
        productCacheEvictor.evictProduct(id);
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public AdminProductResponse adjustStock(Long id, int delta) {
        int updated = productRepository.adjustStock(id, delta, Instant.now());
        if (updated == 0) {
            // 0 筆：商品不存在，或調整後會小於 0
            findOrThrow(id);
            throw new BusinessException(ErrorCode.STOCK_ADJUSTMENT_INVALID);
        }
        productCacheEvictor.evictProduct(id);
        // adjustStock 執行後會清掉一級快取（clearAutomatically），這裡重新查詢拿到最新庫存
        return AdminProductResponse.from(findOrThrow(id));
    }

    /**
     * 上傳或更換商品圖片。
     *
     * <p>檔案不在資料庫交易裡，要自己處理一致性：
     * <ul>
     *   <li>交易成功（commit）：刪除舊圖片</li>
     *   <li>交易失敗（rollback）：刪除剛存的新圖片，資料庫仍指向舊圖片</li>
     * </ul>
     * 用 TransactionSynchronization 在交易結束「之後」才動檔案，避免資料庫還沒確定就先刪掉舊圖。
     */
    @Transactional
    public AdminProductResponse uploadImage(Long id, MultipartFile file) {
        Product product = findOrThrow(id);
        String oldPath = product.getImagePath();
        String newPath = imageStorageService.storeImage(file, IMAGE_SUB_DIR);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    imageStorageService.delete(oldPath);
                } else {
                    imageStorageService.delete(newPath);
                }
            }
        });

        product.setImagePath(newPath);
        productCacheEvictor.evictProduct(id);
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    private Product findOrThrow(Long id) {
        return productRepository.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("找不到商品"));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new NotFoundException("找不到分類"));
    }
}
