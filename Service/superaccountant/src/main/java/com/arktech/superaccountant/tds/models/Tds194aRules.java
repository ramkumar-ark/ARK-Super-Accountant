package com.arktech.superaccountant.tds.models;

import com.arktech.superaccountant.masters.models.InterestPayerType;

import java.math.BigDecimal;

/**
 * Section 194A interest rules that depend on the payer and the deductee.
 *
 * <p>The Act 2025 s.393(1) Table 5(ii) gives banks, co-operative banks and post offices a
 * Rs 50,000 annual threshold (Rs 1,00,000 for senior citizens); Table 5(iii) gives every
 * other payer Rs 10,000. Form 140 Annexure 2 uses 1020/1021 for the bank-like payers and
 * 1022 for the rest. The senior-citizen branch is a seam: it needs the deductee's date of
 * birth, which is not captured yet.
 */
public final class Tds194aRules {

    public static final BigDecimal BANK_THRESHOLD_ANNUAL = new BigDecimal("50000.00");
    public static final BigDecimal OTHER_THRESHOLD_ANNUAL = new BigDecimal("10000.00");
    public static final BigDecimal SENIOR_CITIZEN_THRESHOLD_ANNUAL = new BigDecimal("100000.00");

    private Tds194aRules() {
    }

    public static BigDecimal thresholdAnnual(InterestPayerType payerType) {
        return isBankLike(payerType) ? BANK_THRESHOLD_ANNUAL : OTHER_THRESHOLD_ANNUAL;
    }

    /** Senior-citizen seam; callers pass null/false until deductee dates of birth are stored. */
    public static BigDecimal thresholdAnnual(InterestPayerType payerType, Boolean seniorCitizen) {
        if (Boolean.TRUE.equals(seniorCitizen) && isBankLike(payerType)) {
            return SENIOR_CITIZEN_THRESHOLD_ANNUAL;
        }
        return thresholdAnnual(payerType);
    }

    public static String form140Code(InterestPayerType payerType, Boolean seniorCitizen) {
        if (isBankLike(payerType)) {
            return Boolean.TRUE.equals(seniorCitizen) ? "1020" : "1021";
        }
        return "1022";
    }

    private static boolean isBankLike(InterestPayerType payerType) {
        return payerType != null && payerType.isBankOrPostOffice();
    }
}
