package com.arktech.superaccountant.masters.models;

import java.util.Arrays;

/**
 * Non-government deductor category codes for TDS returns, per the e-TDS file
 * format Annexure 4 (Protean/TIN File Format for Form 26Q, v5.8):
 * K Company, M Branch/Division of Company, P Association of Person (AOP),
 * T Association of Person (Trust), J Artificial Juridical Person,
 * B Body of Individuals, Q Individual/HUF, F Firm.
 * Government/statutory codes (A, S, D, E, G, H, L, N) are out of scope.
 */
public enum DeductorType {

    K, M, P, T, J, B, Q, F;

    public static boolean isValid(String code) {
        return code != null && Arrays.stream(values()).anyMatch(t -> t.name().equals(code));
    }
}
