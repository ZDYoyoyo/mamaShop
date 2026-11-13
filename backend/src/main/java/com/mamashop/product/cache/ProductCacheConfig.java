package com.mamashop.product.cache;

import com.mamashop.common.response.PageResponse;
import com.mamashop.product.dto.ProductDetailResponse;
import com.mamashop.product.dto.ProductSummaryResponse;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品快取（R01）：前台的商品列表與商品詳情存在 Redis。
 *
 * <p>用 Spring Cache 抽象層：讀取的方法標 {@code @Cacheable}（ProductService），
 * 第一次查資料庫並把結果存進 Redis，之後同樣的參數直接從 Redis 拿。
 * 商品有變動時由 {@link ProductCacheEvictor} 清除。
 *
 * <p>Redis 只是加速用：連不到 Redis 時網站照常運作（直接查資料庫），見 {@link #errorHandler()}。
 *
 * <p>快取用哪一種由 {@code spring.cache.type} 決定（application.yml 預設 redis）。
 * 這裡不自己建立 CacheManager，而是「調整」Spring Boot 自動建立的那個：
 * 這樣設定 {@code spring.cache.type=none} 時（例如大部分測試）就會換成不快取的版本，程式完全不用改。
 */
@Configuration
@EnableCaching
public class ProductCacheConfig implements CachingConfigurer {

    /**
     * 每筆快取最多存 10 分鐘。正常情況下商品一變動就會被清除，用不到這個期限；
     * 這是保險：萬一有漏清的情況（例如直接改資料庫），最多 10 分鐘後也會更新。
     */
    static final Duration TTL = Duration.ofMinutes(10);

    @Bean
    RedisCacheManagerBuilderCustomizer productCacheCustomizer(RedisConnectionFactory connectionFactory,
                                                              ObjectMapper objectMapper) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                // key 長這樣：mamashop:productDetail::5（和其他程式共用 Redis 時不會撞名）
                .prefixCacheNameWith("mamashop:")
                // 找不到的商品會丟例外，不會存；保險起見也不存 null
                .disableCachingNullValues();

        // 值用 JSON 存（方便用 redis-cli 直接看內容），而且每種快取指定型別：
        // record 是 final 類別，通用的 JSON serializer 不會記錄型別，讀回來會變成 Map 而轉型失敗
        JavaType pageOfSummaries = objectMapper.getTypeFactory()
                .constructParametricType(PageResponse.class, ProductSummaryResponse.class);

        return builder -> builder
                // 清除「所有列表」時用 SCAN 分批找 key，而不是 KEYS：KEYS 會在 key 很多時卡住整個 Redis
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1000)))
                // 清除的時機（交易 commit 之後）由 ProductCacheEvictor 自己控制，原因寫在那裡
                .withCacheConfiguration(ProductCacheNames.DETAIL, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(objectMapper, ProductDetailResponse.class))))
                .withCacheConfiguration(ProductCacheNames.LIST, defaults.serializeValuesWith(
                        SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(objectMapper, pageOfSummaries))));
    }

    /**
     * Redis 掛掉時不要讓網站跟著掛：快取讀寫失敗只記 log，然後照常查資料庫。
     * （預設的錯誤處理會直接丟例外，顧客就看到 500）
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}
