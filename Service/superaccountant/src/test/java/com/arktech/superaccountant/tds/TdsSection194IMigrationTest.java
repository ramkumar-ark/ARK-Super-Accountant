package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.login.config.DataMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the one-time 194I -> 194I_B split migration against a real schema. Runs in a
 * rolled-back transaction so the shared seed data is untouched.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsSection194IMigrationTest {

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void renamesEveryStored194IValueToTheLandBuildingHalf() {
        jdbc.update("DELETE FROM tds_section_configs WHERE section_code = '194I_B'");
        jdbc.update("INSERT INTO tds_section_configs "
                + "(id, section_code, rate_company, rate_non_company, threshold_annual, higher_rate_if_no_pan) "
                + "VALUES (gen_random_uuid(), '194I', 10, 10, 240000, 20)");
        jdbc.update("INSERT INTO preconfigured_masters "
                + "(id, ledger_name, category, is_active, is_template, tds_section, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'Old Rent', 'EXPENSE', true, false, '194I', now(), now())");
        jdbc.update("INSERT INTO upload_ledger_snapshots "
                + "(id, upload_job_id, organization_id, ledger_name, tds_section, created_at) "
                + "VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'Old Rent', '194I', now())");
        jdbc.update("INSERT INTO validation_findings "
                + "(id, upload_job_id, proposed_value, resolve_status, created_at) "
                + "VALUES (gen_random_uuid(), gen_random_uuid(), '194I', 'OPEN', now())");
        jdbc.update("INSERT INTO tds_findings "
                + "(id, organization_id, workflow_id, upload_job_id, finding_type, severity, "
                + "section, proposed_value, resolve_status, created_at) "
                + "VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), "
                + "'WRONG_SECTION', 'HIGH', '194I', '194I', 'OPEN', now())");
        jdbc.update("INSERT INTO tds_deductee_entry "
                + "(id, workflow_id, deductee_name, pan_valid, deductee_type, section, "
                + "amount_paid, tds_deducted, tds_deposited, is_higher_rate_applied) "
                + "VALUES (gen_random_uuid(), gen_random_uuid(), 'Vendor', false, 'NON_COMPANY', '194I', "
                + "100, 2, 2, false)");

        assertEquals(7, dataMigrationService.split194ISection());

        assertEquals(1, count("tds_section_configs", "section_code = '194I_B'"));
        assertEquals(1, count("preconfigured_masters", "ledger_name = 'Old Rent' AND tds_section = '194I_B'"));
        assertEquals(1, count("upload_ledger_snapshots", "tds_section = '194I_B'"));
        assertEquals(1, count("validation_findings", "proposed_value = '194I_B'"));
        assertEquals(1, count("tds_findings", "section = '194I_B'"));
        assertEquals(1, count("tds_findings", "proposed_value = '194I_B'"));
        assertEquals(1, count("tds_deductee_entry", "section = '194I_B'"));

        assertEquals(0, dataMigrationService.split194ISection());
    }

    @Test
    void retiresLegacyConfigRowWhenLandBuildingRowAlreadyExists() {
        jdbc.update("DELETE FROM tds_section_configs WHERE section_code = '194I_B'");
        jdbc.update("INSERT INTO tds_section_configs "
                + "(id, section_code, rate_company, rate_non_company, threshold_annual, higher_rate_if_no_pan) "
                + "VALUES (gen_random_uuid(), '194I_B', 10, 10, 240000, 20)");
        jdbc.update("INSERT INTO tds_section_configs "
                + "(id, section_code, rate_company, rate_non_company, threshold_annual, higher_rate_if_no_pan) "
                + "VALUES (gen_random_uuid(), '194I', 10, 10, 240000, 20)");
        jdbc.update("INSERT INTO preconfigured_masters "
                + "(id, ledger_name, category, is_active, is_template, tds_section, created_at, updated_at) "
                + "VALUES (gen_random_uuid(), 'Old Rent', 'EXPENSE', true, false, '194I', now(), now())");

        dataMigrationService.split194ISection();

        assertEquals(0, count("tds_section_configs", "section_code = '194I'"));
        assertEquals(1, count("tds_section_configs", "section_code = '194I_B'"));
        assertEquals(1, count("preconfigured_masters", "ledger_name = 'Old Rent' AND tds_section = '194I_B'"));
    }

    private int count(String table, String where) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        return rows == null ? 0 : rows;
    }
}
