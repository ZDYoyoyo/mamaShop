package com.mamashop.order.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 結帳的一次性識別碼（R02），存在 Redis：
 *
 * <pre>
 * key：mamashop:checkout-token:{會員 id}:{token}     TTL 30 分鐘
 * 值： NEW             → 拿到了、還沒用
 *      PROCESSING      → 正在建立訂單
 *      DONE:{訂單編號}  → 已經成功建立
 * </pre>
 *
 * <p>key 包含會員 id：別人的 token 拿來用就是「不存在」，也不可能拿到別人的訂單。
 *
 * <p><b>為什麼用 Lua 腳本</b>：「讀出狀態 → 判斷 → 改成 PROCESSING」必須一次完成。
 * 如果分成 GET 再 SET 兩個指令，兩個同時送出的請求可能都讀到 NEW、都以為自己拿到了，
 * 結果還是建立兩筆訂單。Redis 執行 Lua 腳本時不會插入其他指令（單執行緒），所以整段是原子的。
 *
 * <p><b>Redis 連不到時</b>：發 token 時照樣回一個（只是沒存）；送出訂單時跳過重複檢查、照常下單（記 warn log）。
 * 防重複下單是保護，不是下單的必要條件：Redis 壞掉時寧可冒一點重複下單的風險（前端也會擋連點，F05），
 * 也不要讓所有人都不能下單。
 */
@Service
public class CheckoutTokenService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutTokenService.class);

    public static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "mamashop:checkout-token:";
    private static final String NEW = "NEW";
    private static final String PROCESSING = "PROCESSING";
    private static final String DONE_PREFIX = "DONE:";
    /** 只接受 UUID 格式：避免把任意字串（很長、含奇怪字元）拿來當 Redis key */
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * 認領：NEW → PROCESSING，回傳 CLAIMED；已經是 PROCESSING 或 DONE:xxx 就原樣回傳；不存在回傳 MISSING。
     * KEEPTTL：改值時保留原本的到期時間（Redis 6 以上）。
     */
    private static final RedisScript<String> CLAIM = RedisScript.of("""
            local v = redis.call('GET', KEYS[1])
            if not v then return 'MISSING' end
            if v == 'NEW' then
              redis.call('SET', KEYS[1], 'PROCESSING', 'KEEPTTL')
              return 'CLAIMED'
            end
            return v
            """, String.class);

    /** 下單失敗時還原：PROCESSING → NEW（同一個 token 可以修正後再送一次） */
    private static final RedisScript<String> RELEASE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == 'PROCESSING' then
              redis.call('SET', KEYS[1], 'NEW', 'KEEPTTL')
            end
            return 'OK'
            """, String.class);

    private final StringRedisTemplate redis;

    public CheckoutTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 發一個新的 token（進入結帳頁時） */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(key(userId, token), NEW, TTL);
        } catch (RuntimeException e) {
            log.warn("Redis 無法使用，結帳 token 沒有存起來，這次下單不會檢查重複：{}", e.toString());
        }
        return token;
    }

    /** 認領的結果 */
    public sealed interface Claim {
        /** 拿到了，可以建立訂單 */
        record Claimed() implements Claim { }
        /** 這個 token 已經成功下單過：回傳原本的訂單，不要再建一筆 */
        record AlreadyDone(String orderNo) implements Claim { }
        /** 同一個 token 的另一個請求正在處理 */
        record InProgress() implements Claim { }
        /** 不存在：過期、格式錯誤、別人的、或是亂填的 */
        record Missing() implements Claim { }
        /** Redis 連不到：跳過檢查，照常下單 */
        record Unavailable() implements Claim { }
    }

    public Claim claim(Long userId, String token) {
        if (token == null || !UUID_FORMAT.matcher(token).matches()) {
            return new Claim.Missing();
        }
        String result;
        try {
            result = redis.execute(CLAIM, List.of(key(userId, token)));
        } catch (RuntimeException e) {
            log.warn("Redis 無法使用，這次下單不檢查重複：{}", e.toString());
            return new Claim.Unavailable();
        }
        if ("CLAIMED".equals(result)) return new Claim.Claimed();
        if (PROCESSING.equals(result)) return new Claim.InProgress();
        if (result != null && result.startsWith(DONE_PREFIX)) return new Claim.AlreadyDone(result.substring(DONE_PREFIX.length()));
        return new Claim.Missing();
    }

    /** 訂單建立成功（已 commit）：記下訂單編號，之後同一個 token 再送來就回傳這筆訂單 */
    public void complete(Long userId, String token, String orderNo) {
        try {
            redis.opsForValue().set(key(userId, token), DONE_PREFIX + orderNo, TTL);
        } catch (RuntimeException e) {
            // 訂單已經成立：這裡失敗只代表之後重送時會得到「處理中」，最多 TTL 後自己過期
            log.warn("記錄結帳 token 完成狀態失敗（訂單 {} 已成立）：{}", orderNo, e.toString());
        }
    }

    /** 訂單建立失敗：還原成可以再用 */
    public void release(Long userId, String token) {
        try {
            redis.execute(RELEASE, List.of(key(userId, token)));
        } catch (RuntimeException e) {
            log.warn("還原結帳 token 失敗，顧客需要重新整理結帳頁：{}", e.toString());
        }
    }

    private static String key(Long userId, String token) {
        return KEY_PREFIX + userId + ":" + token;
    }
}
