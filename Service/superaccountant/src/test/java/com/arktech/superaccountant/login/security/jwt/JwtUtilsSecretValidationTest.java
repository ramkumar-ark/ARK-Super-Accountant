package com.arktech.superaccountant.login.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsSecretValidationTest {

    private JwtUtils jwtUtilsWithSecret(String secret) {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        return jwtUtils;
    }

    @Test
    void nullSecret_failsStartupWithClearMessage() {
        assertThatThrownBy(() -> jwtUtilsWithSecret(null).validateJwtSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void unsetSecretResolvesToEmpty_failsStartup() {
        // JWT_SECRET is deliberately absent from application.properties, so an unset
        // environment variable resolves to "" via @Value("${JWT_SECRET:}").
        assertThatThrownBy(() -> jwtUtilsWithSecret("").validateJwtSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void shortSecret_failsStartup() {
        assertThatThrownBy(() -> jwtUtilsWithSecret("too-short").validateJwtSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void secretOfAtLeast32Characters_isAccepted() {
        String secret = "a-perfectly-adequate-jwt-secret-value";
        assertThat(secret.length()).isGreaterThanOrEqualTo(32);
        assertThatCode(() -> jwtUtilsWithSecret(secret).validateJwtSecret())
                .doesNotThrowAnyException();
    }
}
