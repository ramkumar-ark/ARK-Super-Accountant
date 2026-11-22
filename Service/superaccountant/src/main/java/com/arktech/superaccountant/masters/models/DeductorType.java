package com.arktech.superaccountant.masters.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Non-government deductor category codes for TDS returns.
 * Codes and labels per the e-TDS file format Annexure 4
 * (Protean/TIN File Format for Form 26Q, v5.8).
 * Government/statutory codes (A, S, D, E, G, H, L, N) are out of scope.
 */
public enum DeductorType {

    K("Company"),
    M("Branch / Division of Company"),
    P("Association of Person (AOP)"),
    T("Association of Person (Trust)"),
    J("Artificial Juridical Person"),
    B("Body of Individuals"),
    Q("Individual/HUF"),
    F("Firm");

    private final String label;

    DeductorType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isValid(String code) {
        return code != null && Arrays.stream(values()).anyMatch(t -> t.name().equals(code));
    }

    public static Optional<DeductorType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.name().equals(code)).findFirst();
    }
}
