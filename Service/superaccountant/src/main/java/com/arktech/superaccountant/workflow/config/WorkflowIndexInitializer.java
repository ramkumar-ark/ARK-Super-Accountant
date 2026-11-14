package com.arktech.superaccountant.workflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * ddl-auto=update cannot express partial unique indexes, so the
 * (organization_id, type, period_key) WHERE active constraint on workflows
 * is created explicitly at startup.
 */
@Component
public class WorkflowIndexInitializer implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_workflows_active_period
                    ON workflows (organization_id, type, period_key)
                    WHERE active
                    """);
        }
    }
}
