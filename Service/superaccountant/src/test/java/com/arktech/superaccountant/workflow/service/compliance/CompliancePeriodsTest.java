package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CompliancePeriodsTest {

    private static final Function<LocalDate, FilerProfile> NO_PROFILE = d -> null;

    private static FilerProfile profile(FilerFrequency freq, boolean iffOptIn) {
        FilerProfile p = new FilerProfile();
        p.setFrequency(freq);
        p.setIffOptIn(iffOptIn);
        return p;
    }

    @Test
    void monthlyTds_fromFyStartToJuly_givesThreeMonths() {
        List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 1), 4, NO_PROFILE);
        assertEquals(List.of("2025-04", "2025-05", "2025-06"),
                periods.stream().map(PeriodCalendar.PeriodRange::periodKey).toList());
        assertEquals(LocalDate.of(2025, 6, 30), periods.get(2).end());
    }

    @Test
    void tds26q_isQuarterly() {
        List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                WorkflowType.TDS_26Q, LocalDate.of(2025, 4, 1), LocalDate.of(2026, 1, 1), 4, NO_PROFILE);
        assertEquals(List.of("2025-Q1", "2025-Q2", "2025-Q3"),
                periods.stream().map(PeriodCalendar.PeriodRange::periodKey).toList());
        assertEquals(LocalDate.of(2025, 12, 31), periods.get(2).end());
    }

    @Test
    void gstr3b_qrmpFromOctober_mixesGrains() {
        Function<LocalDate, FilerProfile> profileAt = d ->
                d.isBefore(LocalDate.of(2025, 10, 1)) ? NO_PROFILE.apply(d) : profile(FilerFrequency.QRMP, false);
        List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2026, 4, 1), 4, profileAt);
        assertEquals(List.of("2025-04", "2025-05", "2025-06", "2025-07", "2025-08", "2025-09",
                        "2025-Q3", "2025-Q4"),
                periods.stream().map(PeriodCalendar.PeriodRange::periodKey).toList());
        for (int i = 1; i < periods.size(); i++) {
            assertEquals(periods.get(i - 1).end().plusDays(1), periods.get(i).start());
        }
    }

    @Test
    void gstr1_qrmpWithoutIff_staysMonthly() {
        Function<LocalDate, FilerProfile> profileAt = d -> profile(FilerFrequency.QRMP, false);
        List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 1), 4, profileAt);
        assertEquals(List.of("2025-04", "2025-05", "2025-06"),
                periods.stream().map(PeriodCalendar.PeriodRange::periodKey).toList());
    }

    @Test
    void januaryFyStart_quarterKeysUseFyYear() {
        List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                WorkflowType.TDS_26Q, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 7, 1), 1, NO_PROFILE);
        assertEquals(List.of("2025-Q1", "2025-Q2"),
                periods.stream().map(PeriodCalendar.PeriodRange::periodKey).toList());
    }

    @Test
    void toExclusiveNotAfterFrom_isEmpty() {
        assertTrue(CompliancePeriods.periods(
                WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 1), 4, NO_PROFILE)
                .isEmpty());
    }
}
