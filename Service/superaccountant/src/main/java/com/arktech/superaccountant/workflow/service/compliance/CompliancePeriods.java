package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure period enumerator for compliance settings: produces the consecutive
 * period ranges covering [from, toExclusive) for a return type, respecting the
 * filer profile in force at each period start.
 */
public final class CompliancePeriods {

    private CompliancePeriods() {
    }

    public static List<PeriodCalendar.PeriodRange> periods(WorkflowType type, LocalDate from,
            LocalDate toExclusive, int fyStartMonth, Function<LocalDate, FilerProfile> profileAt) {
        List<PeriodCalendar.PeriodRange> periods = new ArrayList<>();
        LocalDate cursor = from;
        while (cursor.isBefore(toExclusive)) {
            boolean quarterly = isQuarterlyGrain(type, cursor, fyStartMonth, profileAt);
            PeriodCalendar.PeriodRange range;
            String key;
            if (quarterly) {
                int fy = PeriodCalendar.financialYearOf(cursor, fyStartMonth);
                int offsetMonths = (cursor.getMonthValue() - fyStartMonth + 12) % 12;
                int quarter = offsetMonths / 3 + 1;
                key = "%04d-Q%d".formatted(fy, quarter);
                range = PeriodCalendar.resolveQuarter(key, fyStartMonth);
            } else {
                YearMonth ym = YearMonth.from(cursor);
                key = "%04d-%02d".formatted(ym.getYear(), ym.getMonthValue());
                range = PeriodCalendar.resolveMonthly(key, fyStartMonth);
            }
            periods.add(range);
            cursor = range.end().plusDays(1);
        }
        return periods;
    }

    private static boolean isQuarterlyGrain(WorkflowType type, LocalDate periodStart, int fyStartMonth,
            Function<LocalDate, FilerProfile> profileAt) {
        return isQuarterlyGrain(profileAt.apply(periodStart), type);
    }

    /**
     * The one grain decision shared by the period enumerator and workflow start
     * validation. QRMP is quarterly for GSTR1 and GSTR3B regardless of IFF opt-in.
     */
    public static boolean isQuarterlyGrain(FilerProfile profile, WorkflowType type) {
        if (type == WorkflowType.TDS_PAYMENT) {
            return false;
        }
        if (type == WorkflowType.TDS_26Q) {
            return true;
        }
        FilerFrequency freq = profile == null ? FilerFrequency.MONTHLY : profile.getFrequency();
        if (freq != FilerFrequency.QRMP) {
            return false;
        }
        if (type == WorkflowType.GSTR1 || type == WorkflowType.GSTR3B) {
            return true;
        }
        return profile != null && profile.isIffOptIn();
    }
}
