package com.arktech.superaccountant.workflow.snapshot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * ddl-auto=update cannot express partial unique indexes, so the live-row
 * constraint on frozen_vouchers is created explicitly at startup.
 */
@Component
public class SnapshotIndexInitializer implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_frozen_vouchers_live
                    ON frozen_vouchers (organization_id, voucher_key)
                    WHERE released_at IS NULL
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_frozen_master_attr_live
                    ON frozen_master_attributes (organization_id, ledger_key, attribute, workflow_type,
                        COALESCE(valid_from, DATE '0001-01-01'))
                    WHERE released_at IS NULL
                    """);
        }
    }
}
