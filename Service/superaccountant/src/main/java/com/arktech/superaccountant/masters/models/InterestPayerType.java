package com.arktech.superaccountant.masters.models;

import java.util.Locale;

/**
 * What kind of payer the company is for section 194A interest.
 *
 * <p>A bank, co-operative bank or post office gets the higher Rs 50,000 annual threshold
 * (and the Form 140 bank codes); every other payer uses the Rs 10,000 threshold. Senior
 * citizens get Rs 1,00,000, which needs the deductee's date of birth and is not captured yet.
 */
public enum InterestPayerType {
    OTHER(false),
    BANK(true),
    CO_OPERATIVE_BANK(true),
    POST_OFFICE(true);

    private final boolean bankOrPostOffice;

    InterestPayerType(boolean bankOrPostOffice) {
        this.bankOrPostOffice = bankOrPostOffice;
    }

    public boolean isBankOrPostOffice() {
        return bankOrPostOffice;
    }

    /** Lenient lookup for values read from storage: blank or unknown means OTHER. */
    public static InterestPayerType from(String value) {
        InterestPayerType parsed = parse(value);
        return parsed == null ? OTHER : parsed;
    }

    /** Strict lookup for user input; blank means OTHER, unknown returns null. */
    public static InterestPayerType parse(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
