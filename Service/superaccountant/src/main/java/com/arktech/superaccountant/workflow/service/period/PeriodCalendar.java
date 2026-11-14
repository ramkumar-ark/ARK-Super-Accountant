package com.arktech.superaccountant.workflow.service.period;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodCalendar {

    private static final Pattern MONTHLY = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern QUARTERLY = Pattern.compile("^(\\d{4})-Q([1-4])$");

    private PeriodCalendar() {
    }

    public record PeriodRange(LocalDate start, LocalDate end, String periodKey, int financialYear) {
    }

    public static int financialYearOf(LocalDate date, int fyStartMonth) {
        int year = date.getYear();
        int startMonth = fyStartMonth;
        if (date.getMonthValue() < startMonth) {
            return year - 1;
        }
        return year;
    }

    public static LocalDate fyStart(int financialYear, int fyStartMonth) {
        return LocalDate.of(financialYear, fyStartMonth, 1);
    }

    public static LocalDate fyEnd(int financialYear, int fyStartMonth) {
        return fyStart(financialYear, fyStartMonth).plusMonths(12).minusDays(1);
    }

    public static boolean isQuarterStart(LocalDate date, int fyStartMonth) {
        int offset = (date.getMonthValue() - fyStartMonth + 12) % 12;
        return date.getDayOfMonth() == 1 && offset % 3 == 0;
    }

    public static PeriodRange resolveMonthly(String periodKey, int fyStartMonth) {
        Matcher m = MONTHLY.matcher(periodKey == null ? "" : periodKey);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid monthly period key: " + periodKey);
        }
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        return new PeriodRange(start, ym.atEndOfMonth(), periodKey, financialYearOf(start, fyStartMonth));
    }

    public static PeriodRange resolveQuarter(String periodKey, int fyStartMonth) {
        Matcher m = QUARTERLY.matcher(periodKey == null ? "" : periodKey);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid quarterly period key: " + periodKey);
        }
        int year = Integer.parseInt(m.group(1));
        int quarter = Integer.parseInt(m.group(2));
        int offsetMonths = (quarter - 1) * 3;
        LocalDate start = fyStart(year, fyStartMonth).plusMonths(offsetMonths);
        LocalDate end = start.plusMonths(3).minusDays(1);
        return new PeriodRange(start, end, periodKey, year);
    }

    public static String previousMonthlyKey(String periodKey) {
        Matcher m = MONTHLY.matcher(periodKey == null ? "" : periodKey);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid monthly period key: " + periodKey);
        }
        YearMonth ym = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).minusMonths(1);
        return "%04d-%02d".formatted(ym.getYear(), ym.getMonthValue());
    }

    public static String previousQuarterKey(String periodKey) {
        Matcher m = QUARTERLY.matcher(periodKey == null ? "" : periodKey);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid quarterly period key: " + periodKey);
        }
        int year = Integer.parseInt(m.group(1));
        int quarter = Integer.parseInt(m.group(2));
        if (quarter == 1) {
            return "%04d-Q4".formatted(year - 1);
        }
        return "%04d-Q%d".formatted(year, quarter - 1);
    }

    public static List<String> quarterMonths(String quarterKey, int fyStartMonth) {
        PeriodRange range = resolveQuarter(quarterKey, fyStartMonth);
        List<String> months = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            YearMonth ym = YearMonth.from(range.start().plusMonths(i));
            months.add("%04d-%02d".formatted(ym.getYear(), ym.getMonthValue()));
        }
        return months;
    }
}
