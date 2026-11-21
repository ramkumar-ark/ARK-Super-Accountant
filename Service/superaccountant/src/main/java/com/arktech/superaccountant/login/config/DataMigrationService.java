package com.arktech.superaccountant.login.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles one-time data migrations that must run atomically.
 * Extracted from DataInitializer so that Spring's AOP proxy intercepts
 * the @Transactional boundary — self-invocation from DataInitializer.run()
 * would bypass the proxy and make @Transactional a no-op on that class.
 */
@Service
public class DataMigrationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Migrates legacy FindingSeverity values (INFO→LOW, WARNING→MEDIUM, ERROR→HIGH).
     * Includes an idempotency short-circuit to avoid three UPDATE round-trips on
     * every boot once the migration is complete.
     * All three UPDATEs execute within a single transaction so a mid-migration
     * failure rolls back the entire set.
     *
     * @return total number of rows updated (0 if already migrated)
     */
    @Transactional
    public int backfillFindingSeverities() {
        // Short-circuit: if no legacy severity values remain, skip all three UPDATEs
        Long legacyCount = (Long) entityManager.createQuery(
                "SELECT COUNT(f) FROM ValidationFinding f WHERE f.severity IN ('INFO', 'WARNING', 'ERROR')")
                .getSingleResult();
        if (legacyCount == 0) return 0;

        int updated = entityManager.createQuery(
                "UPDATE ValidationFinding f SET f.severity = 'LOW' WHERE f.severity = 'INFO'")
                .executeUpdate();
        updated += entityManager.createQuery(
                "UPDATE ValidationFinding f SET f.severity = 'MEDIUM' WHERE f.severity = 'WARNING'")
                .executeUpdate();
        updated += entityManager.createQuery(
                "UPDATE ValidationFinding f SET f.severity = 'HIGH' WHERE f.severity = 'ERROR'")
                .executeUpdate();
        return updated;
    }

    /**
     * Splits the single legacy 194I ("Rent") section into 194I_A (plant, machinery or
     * equipment) and 194I_B (land, building, furniture or fittings). Every stored 194I
     * becomes 194I_B so existing land/building mappings keep their behaviour and rate;
     * plant-hire ledgers are re-mapped by the user afterwards.
     *
     * <p>When a 194I_B config row already exists (a partially migrated database) the
     * legacy 194I config row is retired, since every stored 194I value has already been
     * moved to 194I_B above.
     *
     * @return total number of rows updated (0 once migrated)
     */
    @Transactional
    public int split194ISection() {
        int updated = entityManager.createQuery(
                        "UPDATE PreconfiguredMaster m SET m.tdsSection = '194I_B' WHERE m.tdsSection = '194I'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE UploadLedgerSnapshot s SET s.tdsSection = '194I_B' WHERE s.tdsSection = '194I'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE ValidationFinding f SET f.proposedValue = '194I_B' WHERE f.proposedValue = '194I'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE TdsFinding f SET f.section = '194I_B' WHERE f.section = '194I'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE TdsFinding f SET f.proposedValue = '194I_B' WHERE f.proposedValue = '194I'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE TdsDeducteeEntry e SET e.section = '194I_B' WHERE e.section = '194I'")
                .executeUpdate();

        Long buildingExists = entityManager.createQuery(
                        "SELECT COUNT(c) FROM TdsSectionConfig c WHERE c.sectionCode = '194I_B'", Long.class)
                .getSingleResult();
        if (buildingExists == 0) {
            updated += entityManager.createQuery(
                            "UPDATE TdsSectionConfig c SET c.sectionCode = '194I_B' WHERE c.sectionCode = '194I'")
                    .executeUpdate();
        } else {
            updated += entityManager.createQuery(
                            "DELETE FROM TdsSectionConfig c WHERE c.sectionCode = '194I'")
                    .executeUpdate();
        }
        return updated;
    }

    /**
     * Widens upload_ledger_snapshots.pan from the old varchar(10) to varchar(255) (the width of
     * every other string column on that table) on databases created before the column was widened:
     * an over-length PAN from Tally would otherwise fail the whole masters batch insert. Reads
     * information_schema first, so the ALTER only runs when the column is actually narrower than
     * the target (idempotent, safe to call on every boot, and independent of whatever ddl-auto
     * happens to do). Keeps the raw value — never truncates.
     *
     * @return true when the column was widened by this call
     */
    @Transactional
    public boolean widenUploadLedgerSnapshotPan() {
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT character_maximum_length FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() "
                                + "AND table_name = 'upload_ledger_snapshots' AND column_name = 'pan'")
                .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) return false;
        int currentLength = ((Number) rows.get(0)).intValue();
        if (currentLength >= 255) return false;
        entityManager.createNativeQuery(
                        "ALTER TABLE upload_ledger_snapshots ALTER COLUMN pan TYPE varchar(255)")
                .executeUpdate();
        return true;
    }
}
