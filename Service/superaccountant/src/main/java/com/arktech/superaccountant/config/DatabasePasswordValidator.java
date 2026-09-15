package com.arktech.superaccountant.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Rejects startup when the PostgreSQL password is not configured.
 *
 * The datasource password is sourced from the DB_PASSWORD environment variable with no
 * usable default (see application.properties), mirroring how JWT_SECRET is handled.
 * This runs as a {@link BeanFactoryPostProcessor} so the check happens before the
 * datasource is created, making a missing password fail with a clear error instead of
 * surfacing later as a connection error.
 */
@Component
public class DatabasePasswordValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String databasePassword = environment.getProperty("spring.datasource.password");
        if (databasePassword == null || databasePassword.isBlank()) {
            throw new IllegalStateException(
                "DB_PASSWORD environment variable must be set");
        }
    }
}
