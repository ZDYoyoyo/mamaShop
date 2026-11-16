package com.mamashop.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 登入失敗次數限制（防暴力猜密碼），計數存在 Redis。
 *
 * <pre>
 * mamashop:login-fail:acct:{Email 的雜湊}:{IP}   同一個 IP 對同一個帳號，15 分鐘內失敗 5 次 → 鎖 15 分鐘
 * mamashop:login-fail:ip:{IP}                    同一個 IP 不管猜哪個帳號，15 分鐘內失敗 30 次 → 鎖 15 分鐘
 * </pre>
 *
 * <h2>為什麼這樣設計</h2>
 * <ul>
 *   <li><b>帳號的計數包含 IP</b>，不是只看 Email：只看 Email 的話，任何人故意輸錯 5 次就能把別人（例如管理員）鎖在外面。
 *       加上 IP 之後，被鎖的只有亂猜的那個來源，本人從自己的網路還是登得進去</li>
 *   <li><b>另外有只看 IP 的上限</b>：擋「同一個來源，換著不同帳號各猜幾次」</li>
 *   <li><b>帳號不存在也算一次失敗</b>：不然從「有沒有被鎖」就能看出哪些 Email 有註冊</li>
 *   <li><b>被鎖的期間，連密碼對不對都不檢查</b>：鎖住之後還繼續告訴對方密碼對錯，就等於沒鎖</li>
 *   <li><b>登入成功只清掉那個帳號的計數，不清 IP 的</b>：否則攻擊者每猜幾次就登入一次自己的帳號，IP 的計數永遠不會滿</li>
 *   <li><b>計數＋設定到期時間用 Lua 腳本一次做完</b>（和 R02 一樣的理由）：分成 INCR、EXPIRE 兩個指令的話，
 *       中間失敗會留下一個永遠不會過期的計數，那個帳號就永遠被鎖住</li>
 *   <li><b>從第一次失敗開始算 15 分鐘</b>（固定的時間窗）：之後再失敗不會延長，時間到就全部歸零</li>
 * </ul>
 *
 * <h2>Redis 連不到時</h2>
 * 放行（不限制），只記 log：和商品快取（R01）、防重複下單（R02）一樣，Redis 壞掉不能讓所有人都登不進去。
 * 代價是那段時間沒有次數限制；密碼本身還有 BCrypt（每次比對約 0.1 秒）擋著。
 *
 * <h2>沒有擋的情況</h2>
 * 很多不同 IP 一起猜同一個帳號（每個 IP 都有自己的 5 次）。要擋得再加「只看帳號」的上限，
 * 但那又回到「可以把別人鎖在外面」的問題；以家庭小店來說先不處理，見 docs/NOTES.md。
 */
@Service
@EnableConfigurationProperties(LoginAttemptService.Properties.class)
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final String ACCOUNT_PREFIX = "mamashop:login-fail:acct:";
    private static final String IP_PREFIX = "mamashop:login-fail:ip:";

    /** 計數加一；是第一次（值變成 1）才設定到期時間，之後的失敗不會延長 */
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    /**
     * 對應 application.yml 的 app.login-limit。
     *
     * @param enabled              關閉時完全不限制（測試預設關閉）
     * @param maxFailuresPerAccount 同一個 IP 對同一個帳號最多失敗幾次
     * @param maxFailuresPerIp      同一個 IP 總共最多失敗幾次
     * @param window                計數多久歸零（也是被鎖住的時間上限）
     */
    @ConfigurationProperties(prefix = "app.login-limit")
    public record Properties(boolean enabled, int maxFailuresPerAccount, int maxFailuresPerIp, Duration window) {
    }

    private final StringRedisTemplate redis;
    private final Properties properties;

    public LoginAttemptService(StringRedisTemplate redis, Properties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 這個來源現在是不是被鎖住（登入前檢查） */
    public boolean isBlocked(String normalizedEmail, String ip) {
        if (!properties.enabled()) {
            return false;
        }
        try {
            List<String> counts = redis.opsForValue().multiGet(List.of(accountKey(normalizedEmail, ip), ipKey(ip)));
            return counts != null
                    && (toInt(counts.get(0)) >= properties.maxFailuresPerAccount()
                    || toInt(counts.get(1)) >= properties.maxFailuresPerIp());
        } catch (RuntimeException e) {
            log.warn("Redis 無法使用，這次登入不檢查失敗次數：{}", e.toString());
            return false;
        }
    }

    /** 記一次失敗（Email 或密碼錯誤、帳號不存在、帳號停用） */
    public void recordFailure(String normalizedEmail, String ip) {
        if (!properties.enabled()) {
            return;
        }
        try {
            String ttlMillis = String.valueOf(properties.window().toMillis());
            Long accountFailures = redis.execute(INCREMENT, List.of(accountKey(normalizedEmail, ip)), ttlMillis);
            Long ipFailures = redis.execute(INCREMENT, List.of(ipKey(ip)), ttlMillis);
            // 剛好到上限的那一次記一筆 log：之後被擋的請求不再重複記，避免被攻擊時 log 爆量
            if (accountFailures != null && accountFailures == properties.maxFailuresPerAccount()) {
                log.warn("登入失敗次數達上限，已暫時鎖定：帳號雜湊 {}，來源 {}", hash(normalizedEmail).substring(0, 12), ip);
            }
            if (ipFailures != null && ipFailures == properties.maxFailuresPerIp()) {
                log.warn("同一個來源登入失敗次數達上限，已暫時鎖定：{}", ip);
            }
        } catch (RuntimeException e) {
            log.warn("Redis 無法使用，這次登入失敗沒有記錄：{}", e.toString());
        }
    }

    /** 登入成功：清掉這個來源對這個帳號的失敗計數（IP 的計數不清，原因見類別說明） */
    public void recordSuccess(String normalizedEmail, String ip) {
        if (!properties.enabled()) {
            return;
        }
        try {
            redis.delete(accountKey(normalizedEmail, ip));
        } catch (RuntimeException e) {
            log.warn("Redis 無法使用，登入失敗計數沒有清除：{}", e.toString());
        }
    }

    /** 被鎖住時告訴使用者大約要等多久（分鐘，無條件進位） */
    public long windowMinutes() {
        return Math.max(1, (properties.window().toSeconds() + 59) / 60);
    }

    private static String accountKey(String normalizedEmail, String ip) {
        return ACCOUNT_PREFIX + hash(normalizedEmail) + ":" + ip;
    }

    private static String ipKey(String ip) {
        return IP_PREFIX + ip;
    }

    /**
     * Email 雜湊後才當 key：使用者輸入的任意字串（可能很長、有奇怪字元）不直接放進 Redis 的 key，
     * Redis 裡也不會留下「誰登入失敗過」的明文 Email
     */
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int toInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
