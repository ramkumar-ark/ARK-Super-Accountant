package com.arktech.superaccountant.statutory.payload.request;

import com.arktech.superaccountant.statutory.models.EffectiveDateKind;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A new dated rule row for a statutory section. Rule rows are append-only: changing a value
 * means adding a row from a later date (the open-ended predecessor is end-dated the day
 * before), never editing a row, so analyses of earlier periods stay reproducible.
 * Validation lives in {@code StatutoryRuleValidator} so every rule is unit-testable.
 */
@Data
public class CreateStatutoryRuleRequest {

    /** Payer/payee context; null means DEFAULT. GST rate slabs use the rate text. */
    private String ruleContext;

    private EffectiveDateKind effectiveDateKind;

    private LocalDate effectiveFrom;

    /** Null = open-ended. */
    private LocalDate effectiveTo;

    private BigDecimal rateCompany;

    private BigDecimal rateNonCompany;

    private BigDecimal thresholdPerTransaction;

    private BigDecimal thresholdAnnual;

    private BigDecimal thresholdMonthly;

    private Boolean thresholdExcessOnly;

    private BigDecimal higherRateIfNoPan;

    private String legalReference;

    private String sourceCitation;
}
