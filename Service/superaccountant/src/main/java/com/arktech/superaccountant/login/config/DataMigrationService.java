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
import java.util.Map;

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
            "194J_A", "194J_B", "194J_C", "194M", "194Q", "194D", "193", "194T");

    /**
     * Picker natures for the seeded sections — the exact label copy the frontend has
     * always shown. statutory_sections.description is the single source for these;
     * the legacy TdsSectionConfig descriptions are shorter and stay frozen.
     */
    private static final Map<String, String> DISPLAY_DESCRIPTIONS = Map.ofEntries(
            Map.entry("194A", "Interest other than interest on securities"),
            Map.entry("194C", "Payments to contractors and sub-contractors"),
            Map.entry("194H", "Commission or brokerage"),
            Map.entry("194I_A", "Rent of plant, machinery or equipment"),
            Map.entry("194I_B", "Rent of land, building, furniture or fittings"),
            Map.entry("194J_A", "Fees for technical services, royalty for films, call centre"),
            Map.entry("194J_B", "Fees for professional services, royalty, non-compete fees"),
            Map.entry("194J_C", "Remuneration, fees or commission to a director (not salary)"),
            Map.entry("194M", "Payments by individuals/HUF to contractors and professionals"),
            Map.entry("194Q", "Purchase of goods above the threshold"),
            Map.entry("194D", "Insurance commission"),
            Map.entry("193", "Interest on securities"),
            Map.entry("194T", "Salary, remuneration, commission, bonus or interest to partners of a firm"));

    /**
     * Income-tax Act 2025 s.393 legal references, consulted only at seed time when a section row is
     * first created on a fresh database; statutory_section_rules holds the references afterwards.
     * "194I" maps to the s.393 rent reference for the legacy 194I alias row.
     */
    private static final Map<String, String> S393_LEGAL_REFERENCES = Map.ofEntries(
            Map.entry("194A", "393(1) [Table: Sl. No. 5(ii) and 5(iii)]"),
            Map.entry("194C", "393(1) [Table: Sl. No. 6(i)]"),
            Map.entry("194H", "393(1) [Table: Sl. No. 1(ii)]"),
            Map.entry("194I", "393(1) [Table: Sl. No. 2(ii).D(b)]"),
            Map.entry("194I_A", "393(1) [Table: Sl. No. 2(ii).D(a)]"),
            Map.entry("194I_B", "393(1) [Table: Sl. No. 2(ii).D(b)]"),
            Map.entry("194J_A", "393(1) [Table: Sl. No. 6(iii).D(a)]"),
            Map.entry("194J_B", "393(1) [Table: Sl. No. 6(iii).D(b)]"),
            Map.entry("194J_C", "393(1) [Table: Sl. No. 6(iii).D(b)]"),
            Map.entry("194Q", "393(1) [Table: Sl. No. 8(ii)]"),
            Map.entry("194D", "393(1) [Table: Sl. No. 1(i)]"),
            Map.entry("193", "393(1) [Table: Sl. No. 5(i)]"),
            Map.entry("194T", "393(3) [Table: Sl. No. 7]"));

    /** 194M under the 2025 Act (s.393(1) Table Sl. No. 6(ii)); still reported in Form 141, which is unsupported. */
    private static final String S393_194M_REFERENCE = "393(1) [Table: Sl. No. 6(ii)]";

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

    /**
     * statutory_sections.description is the single source of the picker natures. This
     * keeps the seeded rows on the exact label copy the frontend has always shown
     * (PR1 backfilled the shorter legacy descriptions). Idempotent; runs every startup.
     *
     * @return number of descriptions updated
     */
    @Transactional
    public int updateStatutorySectionDescriptions() {
        int updated = 0;
        for (StatutorySection section
                : statutorySectionRepository.findByStatute(Statute.INCOME_TAX)) {
            String display = DISPLAY_DESCRIPTIONS.get(section.getCode());
            if (display != null && !display.equals(section.getDescription())) {
                section.setDescription(display);
                statutorySectionRepository.save(section);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Seeds the ordinary sections the app never carried: 194D (insurance commission), 193
     * (interest on securities) and 194T (partner payments), each with its Form 26Q / Form 140
     * code (26Q v7.8 Annexure; CPC Form 140 payment codes) and dated rule rows from the dates
     * the rates are verified for: a Finance Act 2025 row (1 April 2025 - 31 March 2026) and the
     * s.393 row from 1 April 2026. Earlier periods are deliberately not seeded; the engine falls
     * back to the earliest row for them. Idempotent per section.
     *
     * <p>Values: 194D 2% (10% domestic company), Rs 20,000 per FY (Finance Act 2025 s.61);
     * 193 10%, Rs 10,000 per FY (Finance Act 2025 s.56); 194T 10%, Rs 20,000 per FY
     * (Finance (No. 2) Act 2024 s.62, from 1 April 2025, payer must be a firm). No-PAN rate 20%.
     *
     * @return number of sections created
     */
    @Transactional
    public int seedFinanceAct2025Sections() {
        int created = 0;
        created += seedSection("194D", "94D", "1005", "10.00", "2.00", "20000.00",
                "Finance Act 2025 s.61 and First Schedule Part II");
        created += seedSection("193", "193", "1019", "10.00", "10.00", "10000.00",
                "Finance Act 2025 s.56 and First Schedule Part II");
        created += seedSection("194T", "94T", "1067", "10.00", "10.00", "20000.00",
                "Finance (No. 2) Act 2024 s.62");
        return created;
    }

    private int seedSection(String code, String code26q, String code140,
                            String rateCompany, String rateNonCompany, String annual,
                            String faCitation) {
        if (statutorySectionRepository.existsByStatuteAndCode(Statute.INCOME_TAX, code)) {
            return 0;
        }
        StatutorySection section = new StatutorySection();
        section.setStatute(Statute.INCOME_TAX);
        section.setCode(code);
        section.setDescription(DISPLAY_DESCRIPTIONS.get(code));
        section.setRpu26qCode(code26q);
        section.setRpu140Code(code140);
        section.setRpu140PayeeSplit(false);
        int order = SORT_ORDER.indexOf(code) + 1;
        section.setSortOrder(order == 0 ? 100 : order);
        statutorySectionRepository.save(section);

        BigDecimal company = new BigDecimal(rateCompany);
        BigDecimal other = new BigDecimal(rateNonCompany);
        BigDecimal threshold = new BigDecimal(annual);
        BigDecimal noPan = new BigDecimal("20.00");
        rule(section, StatutoryRuleContext.DEFAULT, company, other, null, threshold, noPan,
                Tds194aRules.FINANCE_ACT_2025_EFFECTIVE, TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE.minusDays(1),
                code, faCitation);
        rule(section, StatutoryRuleContext.DEFAULT, company, other, null, threshold, noPan,
                TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE, null,
                S393_LEGAL_REFERENCES.get(code), "Income-tax Act 2025 s.393");
        return 1;
    }

    /**
     * Brings the seeded old-regime/new-Act rule rows in line with the Finance Act 2025 text
     * (effective 1 April 2025) and the 2025 Act. The legacy config columns these rows were first
     * copied from are frozen and predate those changes. Idempotent; runs every startup.
     *
     * <ul>
     *   <li>194H: Rs 15,000 until 31 March 2025, Rs 20,000 from then (FA 2025 s.63).</li>
     *   <li>194J_A / 194J_B: Rs 30,000 until 31 March 2025, Rs 50,000 from then (FA 2025 s.65).</li>
     *   <li>194I_A / 194I_B: Rs 2,40,000 per FY until 31 March 2025; from then Rs 50,000 per
     *       month or part of a month (FA 2025 s.64, carried into s.393 Table Sl. No. 2(ii)).</li>
     *   <li>194Q: tax only on the excess over Rs 50 lakh in every period; no-PAN rate 5% from
     *       the 2025 Act.</li>
     *   <li>194M: given a 2025 Act row carrying its s.393 reference. Form 141 reporting is still
     *       unsupported, so RPU generation keeps rejecting it.</li>
     * </ul>
     *
     * @return number of rule rows created or changed
     */
    @Transactional
    public int applyFinanceAct2025RuleCorrections() {
        BigDecimal monthly = new BigDecimal("50000.00");
        int changed = 0;
        changed += splitAtFinanceAct2025("194H", new BigDecimal("15000.00"),
                new BigDecimal("20000.00"), null, "Finance Act 2025 s.63");
        changed += splitAtFinanceAct2025("194J_A", new BigDecimal("30000.00"),
                new BigDecimal("50000.00"), null, "Finance Act 2025 s.65");
        changed += splitAtFinanceAct2025("194J_B", new BigDecimal("30000.00"),
                new BigDecimal("50000.00"), null, "Finance Act 2025 s.65");
        changed += splitAtFinanceAct2025("194I_A", new BigDecimal("240000.00"),
                null, monthly, "Finance Act 2025 s.64");
        changed += splitAtFinanceAct2025("194I_B", new BigDecimal("240000.00"),
                null, monthly, "Finance Act 2025 s.64");
        for (String code : List.of("194I_A", "194I_B")) {
            for (StatutorySectionRule r : defaultRules(code)) {
                if (!r.getEffectiveFrom().isBefore(TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE)
                        && r.getThresholdMonthly() == null) {
                    r.setThresholdMonthly(monthly);
                    r.setThresholdAnnual(null);
                    touch(r);
                    changed++;
                }
            }
        }
        for (StatutorySectionRule r : defaultRules("194Q")) {
            boolean newAct = !r.getEffectiveFrom().isBefore(TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE);
            boolean dirty = false;
            if (!Boolean.TRUE.equals(r.getThresholdExcessOnly())) {
                r.setThresholdExcessOnly(true);
                dirty = true;
            }
            if (newAct && (r.getHigherRateIfNoPan() == null
                    || r.getHigherRateIfNoPan().compareTo(new BigDecimal("5.00")) != 0)) {
                r.setHigherRateIfNoPan(new BigDecimal("5.00"));
                dirty = true;
            }
            if (dirty) {
                touch(r);
                changed++;
            }
        }
        changed += addNewActRowFor194M();
        return changed;
    }

    /**
     * Splits the open old-regime DEFAULT row of a section at the Finance Act 2025 date: the
     * original row now ends 31 March 2025 (carrying the pre-amendment threshold) and a copy
     * from 1 April 2025 carries the amended one. No-op when already split or not present.
     */
    private int splitAtFinanceAct2025(String code, BigDecimal preAnnual,
                                      BigDecimal postAnnual, BigDecimal postMonthly, String citation) {
        LocalDate fa2025 = Tds194aRules.FINANCE_ACT_2025_EFFECTIVE;
        List<StatutorySectionRule> rules = defaultRules(code);
        if (rules.stream().anyMatch(r -> fa2025.equals(r.getEffectiveFrom()))) {
            return 0;
        }
        StatutorySectionRule base = rules.stream()
                .filter(r -> OLD_REGIME_FROM.equals(r.getEffectiveFrom())
                        && (r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(fa2025)))
                .findFirst().orElse(null);
        if (base == null) {
            return 0;
        }
        StatutorySectionRule post = new StatutorySectionRule();
        post.setSectionId(base.getSectionId());
        post.setRuleContext(base.getRuleContext());
        post.setRateCompany(base.getRateCompany());
        post.setRateNonCompany(base.getRateNonCompany());
        post.setThresholdPerTransaction(base.getThresholdPerTransaction());
        post.setThresholdAnnual(postAnnual);
        post.setThresholdMonthly(postMonthly);
        post.setThresholdExcessOnly(base.getThresholdExcessOnly());
        post.setHigherRateIfNoPan(base.getHigherRateIfNoPan());
        post.setEffectiveFrom(fa2025);
        post.setEffectiveTo(base.getEffectiveTo());
        post.setLegalReference(base.getLegalReference());
        post.setSourceCitation(citation);
        statutorySectionRuleRepository.save(post);

        base.setEffectiveTo(fa2025.minusDays(1));
        base.setThresholdAnnual(preAnnual);
        touch(base);
        return 2;
    }

    private int addNewActRowFor194M() {
        LocalDate newAct = TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE;
        List<StatutorySectionRule> rules = defaultRules("194M");
        if (rules.isEmpty() || rules.stream().anyMatch(r -> newAct.equals(r.getEffectiveFrom()))) {
            return 0;
        }
        StatutorySectionRule base = rules.stream()
                .filter(r -> r.getEffectiveTo() == null).findFirst().orElse(null);
        if (base == null) {
            return 0;
        }
        StatutorySectionRule post = new StatutorySectionRule();
        post.setSectionId(base.getSectionId());
        post.setRuleContext(base.getRuleContext());
        post.setRateCompany(base.getRateCompany());
        post.setRateNonCompany(base.getRateNonCompany());
        post.setThresholdPerTransaction(base.getThresholdPerTransaction());
        post.setThresholdAnnual(base.getThresholdAnnual());
        post.setHigherRateIfNoPan(base.getHigherRateIfNoPan());
        post.setEffectiveFrom(newAct);
        post.setLegalReference(S393_194M_REFERENCE);
        post.setSourceCitation("Income-tax Act 2025 s.393");
        statutorySectionRuleRepository.save(post);
        base.setEffectiveTo(newAct.minusDays(1));
        touch(base);
        return 2;
    }

    private List<StatutorySectionRule> defaultRules(String code) {
        return statutorySectionRepository.findByStatuteAndCode(Statute.INCOME_TAX, code)
                .map(sec -> statutorySectionRuleRepository.findBySectionId(sec.getId()).stream()
                        .filter(r -> StatutoryRuleContext.DEFAULT.equals(r.getRuleContext()))
                        .toList())
                .orElse(List.of());
    }

    private void touch(StatutorySectionRule rule) {
        rule.setUpdatedAt(java.time.Instant.now());
        statutorySectionRuleRepository.save(rule);
    }

    private void backfillSection(TdsSectionConfig config) {
        StatutorySection section = new StatutorySection();
        section.setStatute(Statute.INCOME_TAX);
        section.setCode(config.getSectionCode());
        section.setDescription(DISPLAY_DESCRIPTIONS.getOrDefault(
                config.getSectionCode(), config.getDescription()));
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
                    S393_LEGAL_REFERENCES.get(config.getSectionCode()),
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
