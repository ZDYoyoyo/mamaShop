package com.mamashop.order.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 訂單編號：M + 台灣日期 yyMMdd + 10 位隨機數字，例如 M2611128402719365（17 字元）。
 *
 * <ul>
 *   <li>不用資料庫流水號：流水號容易被猜（改 1 號、2 號就能試別人的訂單），也會透露每天的訂單量</li>
 *   <li>前面帶日期：家人看編號就知道是哪天的訂單，電話客服也好溝通</li>
 *   <li>SecureRandom：無法從前一個編號推算下一個</li>
 * </ul>
 * 重複的機率極低（同一天 100 筆訂單約百萬分之一）；真的重複時資料庫的 UNIQUE 會擋下，
 * 整筆交易回滾（庫存不會被扣），顧客重新送出即可。
 */
@Component
public class OrderNumberGenerator {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final long RANDOM_BOUND = 10_000_000_000L; // 10 位數

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OrderNumberGenerator() {
        this(Clock.system(TAIPEI));
    }

    /** 測試用：固定日期 */
    OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        return "M" + LocalDate.now(clock.withZone(TAIPEI)).format(DATE)
                + String.format("%010d", random.nextLong(RANDOM_BOUND));
    }
}
