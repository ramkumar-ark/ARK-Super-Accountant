package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.EffectiveDateKind;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutoryRuleRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per validation rule for app-administrator rule writes (plan §4): append-only
 * dated rows, never overlapping per (section, context), with the open predecessor
 * superseded to the day before the new row starts.
 */
class StatutoryRuleValidatorTest {

    private StatutorySection tdsSection() {
        StatutorySection s = new StatutorySection();
        s.setStatute(Statute.INCOME_TAX);
        s.setCode("194C");
        s.setActive(true);
        return s;
    }

    private StatutorySection cgstSection(String code) {
        StatutorySection s = new StatutorySection();
        s.setStatute(Statute.CGST);
        s.setCode(code);
        s.setActive(true);
        return s;
    }

    private CreateStatutoryRuleRequest tdsRequest(LocalDate from) {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.FINANCIAL_YEAR_START);
        r.setEffectiveFrom(from);
        r.setRateCompany(new BigDecimal("2"));
        r.setRateNonCompany(new BigDecimal("1"));
        r.setHigherRateIfNoPan(new BigDecimal("20"));
        r.setSourceCitation("Income-tax Act 2025 s.393");
        return r;
    }

    private StatutorySectionRule row(String context, LocalDate from, LocalDate to) {
        StatutorySectionRule row = new StatutorySectionRule();
        row.setRuleContext(context);
        row.setEffectiveFrom(from);
        row.setEffectiveTo(to);
        row.setRateCompany(new BigDecimal("2"));
        row.setRateNonCompany(new BigDecimal("1"));
        row.setHigherRateIfNoPan(new BigDecimal("20"));
        return row;
    }

    private List<String> errors(StatutoryRuleValidator.Result result) {
        return result.errors();
    }

    @Test
    void rateBoundsRejected0And100Accepted() {
        for (BigDecimal bad : new BigDecimal[] {new BigDecimal("-0.01"), new BigDecimal("100.01")}) {
            CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
            r.setRateCompany(bad);
            assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                    .anyMatch(e -> e.contains("rateCompany")));
        }
        for (BigDecimal good : new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("100")}) {
            CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
            r.setRateCompany(good);
            assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).isEmpty());
        }
    }

    @Test
    void negativeThresholdRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setThresholdAnnual(new BigDecimal("-1"));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                .anyMatch(e -> e.contains("thresholdAnnual")));
    }

    @Test
    void financialYearStartMustBe1April() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 2));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                .anyMatch(e -> e.contains("effectiveFrom")));

        CreateStatutoryRuleRequest ok = tdsRequest(LocalDate.of(2027, 4, 1));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), ok, List.of())).isEmpty());
    }

    @Test
    void statutoryDateAcceptsExactMidYearDate() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2024, 8, 1));
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).isEmpty());
    }

    @Test
    void effectiveToBeforeFromRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setEffectiveTo(LocalDate.of(2026, 3, 31));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                .anyMatch(e -> e.contains("effectiveTo")));
    }

    @Test
    void blankSourceCitationRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setSourceCitation(" ");
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                .anyMatch(e -> e.contains("sourceCitation")));
    }

    @Test
    void citationAndLegalReferenceMaxLength() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setSourceCitation("x".repeat(256));
        r.setLegalReference("y".repeat(256));
        List<String> errors = errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.contains("sourceCitation")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("legalReference")));
    }

    @Test
    void overlapWithClosedRowRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        // Existing DEFAULT row fully closes after the new row starts.
        List<StatutorySectionRule> existing = new ArrayList<>();
        existing.add(row("DEFAULT", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 12, 31)));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, existing)).stream()
                .anyMatch(e -> e.contains("overlap")));
    }

    @Test
    void newFromAtOrBeforeLatestExistingFromRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2026, 4, 1));
        List<StatutorySectionRule> existing = new ArrayList<>();
        existing.add(row("DEFAULT", LocalDate.of(2026, 4, 1), null));
        List<String> errors = errors(StatutoryRuleValidator.validate(tdsSection(), r, existing));
        assertTrue(errors.stream().anyMatch(e -> e.contains("append-only") || e.contains("overlap")));
    }

    @Test
    void openPredecessorSupersededToDayBefore() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        List<StatutorySectionRule> existing = new ArrayList<>();
        existing.add(row("DEFAULT", LocalDate.of(2026, 4, 1), null));
        StatutoryRuleValidator.Result result = StatutoryRuleValidator.validate(tdsSection(), r, existing);
        assertTrue(result.errors().isEmpty());
        assertEquals(LocalDate.of(2027, 3, 31), result.superseded().getEffectiveTo());
        assertEquals(LocalDate.of(2026, 4, 1), result.superseded().getEffectiveFrom());
    }

    @Test
    void closedPredecessorWithLaterEndStillAllowed() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        List<StatutorySectionRule> existing = new ArrayList<>();
        existing.add(row("DEFAULT", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31)));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, existing)).isEmpty());
    }

    @Test
    void contextsArePerSectionSoOtherContextRowsDoNotOverlap() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setRuleContext("BANK");
        List<StatutorySectionRule> existing = new ArrayList<>();
        existing.add(row("DEFAULT", LocalDate.of(2026, 4, 1), null));
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, existing)).isEmpty());
    }

    @Test
    void tdsRequiresRates() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setRateNonCompany(null);
        List<String> errors = errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rateNonCompany")));
    }

    @Test
    void tdsContextMustBeKnownValue() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setRuleContext("SOMETHING");
        assertTrue(errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of())).stream()
                .anyMatch(e -> e.contains("ruleContext")));
    }

    @Test
    void inactiveSectionRejected() {
        StatutorySection s = tdsSection();
        s.setActive(false);
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        assertTrue(errors(StatutoryRuleValidator.validate(s, r, List.of())).stream()
                .anyMatch(e -> e.contains("activate")));
    }

    @Test
    void b2clThresholdRequiresAnnualAndNoRatesAndNoEffectiveTo() {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        r.setSourceCitation("CGST Act s.10");
        r.setRateCompany(new BigDecimal("5"));
        r.setThresholdAnnual(new BigDecimal("500000"));
        r.setEffectiveTo(LocalDate.of(2027, 3, 31));
        List<String> errors = errors(
                StatutoryRuleValidator.validate(cgstSection("B2CL_THRESHOLD"), r, List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.contains("rates must be empty")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("effectiveTo")));
    }

    @Test
    void b2clThresholdHappyPath() {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        r.setSourceCitation("CGST Act s.10");
        r.setThresholdAnnual(new BigDecimal("500000"));
        StatutoryRuleValidator.Result result = StatutoryRuleValidator.validate(
                cgstSection("B2CL_THRESHOLD"), r, List.of());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void gstRateContextMustEqualRateCompanyText() {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        r.setSourceCitation("CGST Act");
        r.setRateCompany(new BigDecimal("5.00"));
        r.setRuleContext("12");
        List<String> errors = errors(
                StatutoryRuleValidator.validate(cgstSection("GST_RATE"), r, List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.contains("ruleContext")));
    }

    @Test
    void gstRateHappyPathAndNonCompanyMirrors() {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        r.setSourceCitation("CGST Act");
        r.setRateCompany(new BigDecimal("5.00"));
        r.setRuleContext("5");
        r.setRateNonCompany(new BigDecimal("5.00"));
        assertTrue(errors(
                StatutoryRuleValidator.validate(cgstSection("GST_RATE"), r, List.of())).isEmpty());
    }

    @Test
    void unknownCgstCodeNotEditable() {
        CreateStatutoryRuleRequest r = new CreateStatutoryRuleRequest();
        r.setEffectiveDateKind(EffectiveDateKind.STATUTORY_DATE);
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        r.setSourceCitation("CGST Act");
        assertTrue(errors(
                StatutoryRuleValidator.validate(cgstSection("SOMETHING_ELSE"), r, List.of())).stream()
                .anyMatch(e -> e.contains("not editable")));
    }

    @Test
    void missingKindOrDateOrCitationRejected() {
        CreateStatutoryRuleRequest r = tdsRequest(LocalDate.of(2027, 4, 1));
        r.setEffectiveDateKind(null);
        r.setEffectiveFrom(null);
        r.setSourceCitation(null);
        List<String> errors = errors(StatutoryRuleValidator.validate(tdsSection(), r, List.of()));
        assertTrue(errors.stream().anyMatch(e -> e.contains("effectiveDateKind")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("effectiveFrom")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("sourceCitation")));
    }
}
