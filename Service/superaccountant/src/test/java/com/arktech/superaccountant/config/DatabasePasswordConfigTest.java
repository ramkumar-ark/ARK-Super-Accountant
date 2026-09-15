package com.arktech.superaccountant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabasePasswordConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DatabasePasswordValidator.class);

    @Test
    void applicationPropertiesWithoutDbPassword_failsStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD");
        });
    }

    @Test
    void emptyDbPassword_failsStartup() {
        contextRunner
                .withPropertyValues("DB_PASSWORD=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("DB_PASSWORD");
                });
    }

    @Test
    void dbPasswordSupplied_starts() {
        contextRunner
                .withPropertyValues("DB_PASSWORD=test-db-password")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(DatabasePasswordValidator.class));
    }
}
