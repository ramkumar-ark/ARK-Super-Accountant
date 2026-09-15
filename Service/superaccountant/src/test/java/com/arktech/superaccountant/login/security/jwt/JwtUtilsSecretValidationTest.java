package com.arktech.superaccountant.login.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsSecretValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(JwtUtils.class);

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
    void applicationPropertiesWithoutJwtSecret_failsStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        });
    }

    @Test
    void applicationPropertiesWithSuppliedJwtSecret_starts() {
        contextRunner
                .withPropertyValues("JWT_SECRET=a-perfectly-adequate-jwt-secret-value")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtUtils.class));
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
