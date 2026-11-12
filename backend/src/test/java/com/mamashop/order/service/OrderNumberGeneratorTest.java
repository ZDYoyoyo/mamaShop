package com.mamashop.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

    @Test
    void usesTaiwanDate() {
        // UTC 2026-11-11 17:00 = 台灣 2026-11-12 01:00 → 日期要是 261112
        Clock utc = Clock.fixed(Instant.parse("2026-11-11T17:00:00Z"), ZoneOffset.UTC);

        String orderNo = new OrderNumberGenerator(utc).generate();

        assertThat(orderNo).matches("M261112\\d{10}");
    }

    @Test
    void numbersAreNotRepeated() {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(10_000);
    }
}
