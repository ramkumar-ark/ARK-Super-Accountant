package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.masters.models.InterestPayerType;
import com.arktech.superaccountant.tds.models.Tds194aRules;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tds194aRulesTest {

    @Test
    void bankLikePayersUseFiftyThousandAndOthersTenThousand() {
        assertEquals("50000.00", Tds194aRules.thresholdAnnual(InterestPayerType.BANK).toPlainString());
        assertEquals("50000.00", Tds194aRules.thresholdAnnual(InterestPayerType.CO_OPERATIVE_BANK).toPlainString());
        assertEquals("50000.00", Tds194aRules.thresholdAnnual(InterestPayerType.POST_OFFICE).toPlainString());
        assertEquals("10000.00", Tds194aRules.thresholdAnnual(InterestPayerType.OTHER).toPlainString());
        assertEquals("10000.00", Tds194aRules.thresholdAnnual(null).toPlainString());
    }

    @Test
    void seniorCitizenSeamOnlyLiftsBankLikePayers() {
        assertEquals("100000.00", Tds194aRules.thresholdAnnual(InterestPayerType.BANK, true).toPlainString());
        assertEquals("50000.00", Tds194aRules.thresholdAnnual(InterestPayerType.BANK, false).toPlainString());
        assertEquals("50000.00", Tds194aRules.thresholdAnnual(InterestPayerType.BANK, null).toPlainString());
        assertEquals("10000.00", Tds194aRules.thresholdAnnual(InterestPayerType.OTHER, true).toPlainString());
    }

    @Test
    void form140CodeSeparatesBankOtherAndSeniorPayer() {
        assertEquals("1020", Tds194aRules.form140Code(InterestPayerType.BANK, true));
        assertEquals("1021", Tds194aRules.form140Code(InterestPayerType.BANK, false));
        assertEquals("1021", Tds194aRules.form140Code(InterestPayerType.POST_OFFICE, null));
        assertEquals("1022", Tds194aRules.form140Code(InterestPayerType.OTHER, true));
        assertEquals("1022", Tds194aRules.form140Code(null, null));
    }

    @Test
    void payerTypeParsingIsCaseInsensitiveAndFallsBackToOther() {
        assertEquals(InterestPayerType.BANK, InterestPayerType.parse(" bank "));
        assertEquals(InterestPayerType.CO_OPERATIVE_BANK, InterestPayerType.parse("co_operative_bank"));
        assertEquals(InterestPayerType.OTHER, InterestPayerType.parse(""));
        assertEquals(InterestPayerType.OTHER, InterestPayerType.from("UNKNOWN"));
        assertTrue(InterestPayerType.BANK.isBankOrPostOffice());
        assertTrue(!InterestPayerType.OTHER.isBankOrPostOffice());
    }

    @Test
    void thresholdByPeriod() {
        assertEquals("40000.00", Tds194aRules.thresholdAnnualForPeriod(InterestPayerType.BANK, LocalDate.of(2024, 6, 1)).toPlainString());
        assertEquals("50000.00", Tds194aRules.thresholdAnnualForPeriod(InterestPayerType.BANK, LocalDate.of(2025, 6, 1)).toPlainString());
        assertEquals("40000.00", Tds194aRules.thresholdAnnualForPeriod(InterestPayerType.CO_OPERATIVE_BANK, LocalDate.of(2024, 6, 1)).toPlainString());
        assertEquals("10000.00", Tds194aRules.thresholdAnnualForPeriod(InterestPayerType.OTHER, LocalDate.of(2024, 6, 1)).toPlainString());
    }
}
