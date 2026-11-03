package com.arktech.superaccountant.masters.util;

public final class LedgerNames {
    private LedgerNames() {}

    /** Ledger-name matching key: trim + lowercase. Shared by mismatch detection and finding overrides. */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
