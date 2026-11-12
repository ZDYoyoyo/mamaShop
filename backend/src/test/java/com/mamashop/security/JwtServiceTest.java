package com.mamashop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExampleSecret() {
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties("change-me-to-a-random-string-at-least-32-chars", Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parseGarbage_returnsEmpty() {
        JwtService service = new JwtService(new JwtProperties("x".repeat(48), Duration.ofDays(1)));

        assertThat(service.parse("not.a.jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }
}
