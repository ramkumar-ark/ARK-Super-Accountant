package com.arktech.superaccountant.login.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
