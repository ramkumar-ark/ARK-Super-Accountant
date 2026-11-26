package com.arktech.superaccountant.login.config;

import com.arktech.superaccountant.login.models.DataMigrationMarker;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.tds.models.Tds194aRules;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.models.TdsSectionRefs;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.tds.rpu.Rpu140FormatSpec;
import com.arktech.superaccountant.tds.rpu.RpuFormatSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles one-time data migrations that must run atomically.
 * Extracted from DataInitializer so that Spring's AOP proxy intercepts
 * the @Transactional boundary — self-invocation from DataInitializer.run()
 * would bypass the proxy and make @Transactional a no-op on that class.
 */
@Service
public class DataMigrationService {

    /** Marker name for the one-time legacy deductor type P (Individual/HUF) → Q correction. */
    public static final String MARKER_DEDUCTOR_TYPE_P_TO_Q = "deductor_type_p_to_q";

    /**
     * Effective-from date for the old-regime rule rows: any date the engine can
     * resolve (the 1961 Act sections predate the app, so a fixed pre-history date
     * keeps every real TDS date covered).
     */
    private static final LocalDate OLD_REGIME_FROM = LocalDate.of(2000, 4, 1);

    /** Display order of the known sections; anything else sorts last. */
    private static final List<String> SORT_ORDER = List.of(
            "194A", "194C", "194H", "194I_A", "194I_B",
            "194J_A", "194J_B", "194J_C", "194M", "194Q");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private StatutorySectionRepository statutorySectionRepository;

    @Autowired
    private StatutorySectionRuleRepository statutorySectionRuleRepository;

    @Autowired
    private TdsSectionConfigRepository tdsSectionConfigRepository;

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
     * Corrects the legacy deductor type mislabel: the old UI offered "Individual / HUF"
     * under code P, but per e-TDS file format Annexure 4 P is Association of Person (AOP)
     * and Individual/HUF is Q. Every stored P at the time of the fix came from that option,
     * so both organizations and 26Q detail snapshots are rewritten P → Q once.
     *
     * <p>Guarded by a {@code DataMigrationMarker} row rather than a "no P left" check:
     * after this fix P is a legitimately selectable AOP value, so an unconditional
     * re-run on every boot would silently convert users' AOP selections back to Q.
     *
     * @return total number of rows updated (0 if already migrated or nothing to convert)
     */
    @Transactional
    public int migrateIndividualHufDeductorTypePToQ() {
        Long applied = (Long) entityManager.createQuery(
                        "SELECT COUNT(m) FROM DataMigrationMarker m WHERE m.name = :name", Long.class)
                .setParameter("name", MARKER_DEDUCTOR_TYPE_P_TO_Q)
                .getSingleResult();
        if (applied > 0) return 0;

        int updated = entityManager.createQuery(
                        "UPDATE Organization o SET o.deductorType = 'Q' WHERE o.deductorType = 'P'")
                .executeUpdate();
        updated += entityManager.createQuery(
                        "UPDATE Tds26qDetail d SET d.deductorType = 'Q' WHERE d.deductorType = 'P'")
                .executeUpdate();
        // Persist even when 0 rows matched (fresh database): the marker must exist
        // before any future P can be a genuine AOP selection.
        entityManager.persist(new DataMigrationMarker(MARKER_DEDUCTOR_TYPE_P_TO_Q));
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

    /**
     * Backfills the global statutory_sections / statutory_section_rules tables from the
     * seeded TdsSectionConfig rows. For every config row: one section (RPU 26Q/140 codes
     * mirrored from the RPU specs, 194C flagged as the 140 payee-split section) plus a
     * DEFAULT rule row for the old regime (open-ended back to OLD_REGIME_FROM, closed on
     * 31 March 2026 when the section has new-Act values) and one for s.393 from 1 April
     * 2026. 194A additionally gets BANK / OTHER_PAYER / SENIOR_CITIZEN context rows for
     * the Finance Act 2025 thresholds it has always applied in code (Tds194aRules).
     *
     * <p>Idempotent per section: guarded by existsByStatuteAndCode, so re-running on a
     * populated database creates nothing. Values mirror the config columns exactly, so
     * effectiveFor over the dated rows must stay byte-identical to the legacy
     * effectiveForFromColumns resolution for both regimes.
     *
     * <p>One-way copy: after this runs, statutory_section_rules is the authoritative source for
     * rates and thresholds. The TdsSectionConfig rate/threshold/newAct* columns and
     * DataInitializer.NEW_ACT_VALUES are legacy, frozen and never re-synced (sections that
     * already exist are skipped); they are slated for removal in PR2/PR3. Rate/threshold
     * edits must go to statutory_section_rules.
     *
     * <p>RPU generation keeps reading its own code maps this PR; the statutory table
     * becomes its source in a later PR.
     *
     * @return number of sections created (0 when already backfilled)
     */
    @Transactional
    public int backfillStatutorySections() {
        int created = 0;
        for (TdsSectionConfig config : tdsSectionConfigRepository.findAll()) {
            if (statutorySectionRepository.existsByStatuteAndCode(
                    Statute.INCOME_TAX, config.getSectionCode())) {
                continue;
            }
            backfillSection(config);
            created++;
        }
        return created;
    }

    private void backfillSection(TdsSectionConfig config) {
        StatutorySection section = new StatutorySection();
        section.setStatute(Statute.INCOME_TAX);
        section.setCode(config.getSectionCode());
        section.setDescription(config.getDescription());
        section.setRpu26qCode(RpuFormatSpec.rpu26qCode(config.getSectionCode()));
        section.setRpu140Code(Rpu140FormatSpec.rpu140Code(config.getSectionCode()));
        section.setRpu140PayeeSplit("194C".equals(config.getSectionCode()));
        section.setSortOrder(SORT_ORDER.indexOf(config.getSectionCode()) + 1);
        if (section.getSortOrder() == 0) {
            section.setSortOrder(100);
        }
        statutorySectionRepository.save(section);

        boolean hasNewAct = config.getNewActRateCompany() != null;
        // Old regime: the values stored on the config row, with the pre-2026 legal
        // reference (the 1961 Act section code itself).
        rule(section, StatutoryRuleContext.DEFAULT,
                config.getRateCompany(), config.getRateNonCompany(),
                config.getThresholdPerTransaction(), config.getThresholdAnnual(),
                config.getHigherRateIfNoPan(),
                OLD_REGIME_FROM,
                hasNewAct ? TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE.minusDays(1) : null,
                config.getSectionCode(),
                "Income-tax Act 1961 s." + config.getSectionCode());
        if (hasNewAct) {
            rule(section, StatutoryRuleContext.DEFAULT,
                    config.getNewActRateCompany(), config.getNewActRateNonCompany(),
                    config.getNewActThresholdPerTransaction(), config.getNewActThresholdAnnual(),
                    config.getHigherRateIfNoPan(),
                    TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE, null,
                    TdsSectionRefs.newActRef(config.getSectionCode()),
                    "Income-tax Act 2025 s.393");
        }
        if ("194A".equals(config.getSectionCode())) {
            backfill194aContextRules(section, config);
        }
    }

    private void backfill194aContextRules(StatutorySection section, TdsSectionConfig config) {
        LocalDate fa2025 = Tds194aRules.FINANCE_ACT_2025_EFFECTIVE;
        LocalDate newAct = TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE;
        BigDecimal rateCompany = config.getRateCompany();
        BigDecimal rateNonCompany = config.getRateNonCompany();
        BigDecimal noPanRate = config.getHigherRateIfNoPan();
        String oldCite = "Income-tax Act 1961 s.194A";
        String fa2025Cite = "Finance Act 2025 s.58";

        // Bank-like payers (s.393 Table Sl. No. 5(ii)): Rs 40,000 before 1 April 2025,
        // Rs 50,000 from then (Finance Act 2025), unchanged by the 2025 Act.
        rule(section, StatutoryRuleContext.BANK, rateCompany, rateNonCompany, null,
                Tds194aRules.OLD_ACT_BANK_THRESHOLD_ANNUAL, noPanRate,
                OLD_REGIME_FROM, fa2025.minusDays(1), "194A", oldCite);
        rule(section, StatutoryRuleContext.BANK, rateCompany, rateNonCompany, null,
                Tds194aRules.BANK_THRESHOLD_ANNUAL, noPanRate,
                fa2025, newAct.minusDays(1), "194A", fa2025Cite);
        rule(section, StatutoryRuleContext.BANK, rateCompany, rateNonCompany, null,
                Tds194aRules.BANK_THRESHOLD_ANNUAL, noPanRate,
                newAct, null, "393(1) [Table: Sl. No. 5(ii)]", "Income-tax Act 2025 s.393");

        // Every other payer (s.393 Table Sl. No. 5(iii)): Rs 5,000 before 1 April 2025,
        // Rs 10,000 from then (Finance Act 2025), unchanged by the 2025 Act.
        rule(section, StatutoryRuleContext.OTHER_PAYER, rateCompany, rateNonCompany, null,
                Tds194aRules.OLD_ACT_OTHER_THRESHOLD_ANNUAL, noPanRate,
                OLD_REGIME_FROM, fa2025.minusDays(1), "194A", oldCite);
        rule(section, StatutoryRuleContext.OTHER_PAYER, rateCompany, rateNonCompany, null,
                Tds194aRules.OTHER_THRESHOLD_ANNUAL, noPanRate,
                fa2025, newAct.minusDays(1), "194A", fa2025Cite);
        rule(section, StatutoryRuleContext.OTHER_PAYER, rateCompany, rateNonCompany, null,
                Tds194aRules.OTHER_THRESHOLD_ANNUAL, noPanRate,
                newAct, null, "393(1) [Table: Sl. No. 5(iii)]", "Income-tax Act 2025 s.393");

        // Senior citizens (bank-like payers only — that seam stays gated in code):
        // Rs 1,00,000 from the 2025 Act, Table Sl. No. 5(ii).
        rule(section, StatutoryRuleContext.SENIOR_CITIZEN, rateCompany, rateNonCompany, null,
                Tds194aRules.SENIOR_CITIZEN_THRESHOLD_ANNUAL, noPanRate,
                newAct, null, "393(1) [Table: Sl. No. 5(ii)]", "Income-tax Act 2025 s.393");
    }

    private StatutorySectionRule rule(StatutorySection section, String context,
                                      BigDecimal rateCompany, BigDecimal rateNonCompany,
                                      BigDecimal thresholdPerTransaction, BigDecimal thresholdAnnual,
                                      BigDecimal higherRateIfNoPan,
                                      LocalDate effectiveFrom, LocalDate effectiveTo,
                                      String legalReference, String sourceCitation) {
        StatutorySectionRule r = new StatutorySectionRule();
        r.setSectionId(section.getId());
        r.setRuleContext(context);
        r.setRateCompany(rateCompany);
        r.setRateNonCompany(rateNonCompany);
        r.setThresholdPerTransaction(thresholdPerTransaction);
        r.setThresholdAnnual(thresholdAnnual);
        r.setHigherRateIfNoPan(higherRateIfNoPan);
        r.setEffectiveFrom(effectiveFrom);
        r.setEffectiveTo(effectiveTo);
        r.setLegalReference(legalReference);
        r.setSourceCitation(sourceCitation);
        return statutorySectionRuleRepository.save(r);
    }
}
