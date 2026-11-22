package com.arktech.superaccountant.login.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the one-time legacy deductor type P (old Individual/HUF option) → Q
 * correction against a real schema. Runs in a rolled-back transaction so the shared
 * seed data is untouched. The boot-time DataInitializer already ran the migration
 * once when the context started, so each test manages the marker row explicitly.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DeductorTypeMigrationTest {

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void rewritesLegacyPToQInOrganizationsAndDetailsThenNeverAgain() {
        jdbc.update("DELETE FROM data_migration_markers WHERE name = ?",
                DataMigrationService.MARKER_DEDUCTOR_TYPE_P_TO_Q);
        jdbc.update("INSERT INTO organizations "
                + "(id, name, financial_year_start, deductor_type, created_at) "
                + "VALUES (gen_random_uuid(), 'Legacy Sole Prop', 4, 'P', now())");
        jdbc.update("INSERT INTO tds_26q_detail (id, workflow_id, deductor_type) "
                + "VALUES (gen_random_uuid(), gen_random_uuid(), 'P')");

        assertEquals(2, dataMigrationService.migrateIndividualHufDeductorTypePToQ());
        // The marker was written via JPA; flush it so the raw JDBC counts below see it.
        entityManager.flush();

        assertEquals(0, count("organizations", "deductor_type = 'P'"));
        assertEquals(1, count("organizations", "deductor_type = 'Q' AND name = 'Legacy Sole Prop'"));
        assertEquals(0, count("tds_26q_detail", "deductor_type = 'P'"));
        assertEquals(1, count("tds_26q_detail", "deductor_type = 'Q'"));
        assertEquals(1, count("data_migration_markers",
                "name = '" + DataMigrationService.MARKER_DEDUCTOR_TYPE_P_TO_Q + "'"));

        assertEquals(0, dataMigrationService.migrateIndividualHufDeductorTypePToQ());
    }

    @Test
    void leavesGenuinePSelectionsAloneOnceMarkerExists() {
        // Marker present (as after any completed boot): P now means AOP and must survive.
        jdbc.update("INSERT INTO data_migration_markers (name, applied_at) "
                + "VALUES (?, now()) ON CONFLICT (name) DO NOTHING",
                DataMigrationService.MARKER_DEDUCTOR_TYPE_P_TO_Q);
        jdbc.update("INSERT INTO organizations "
                + "(id, name, financial_year_start, deductor_type, created_at) "
                + "VALUES (gen_random_uuid(), 'AOP Client', 4, 'P', now())");

        assertEquals(0, dataMigrationService.migrateIndividualHufDeductorTypePToQ());

        assertEquals(1, count("organizations", "deductor_type = 'P' AND name = 'AOP Client'"));
        assertTrue(count("data_migration_markers",
                "name = '" + DataMigrationService.MARKER_DEDUCTOR_TYPE_P_TO_Q + "'") >= 1);
    }

    private int count(String table, String where) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        return rows == null ? 0 : rows;
    }
}
