package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.masters.models.DeductorType;

import java.util.Map;
import java.util.Optional;

/**
 * The deductor class of an organization, from its e-TDS deductor type or,
 * when that is unset, from the 4th character of its PAN (income-tax PAN structure).
 *
 * <p>PAN 4th character mapping: C Company→K, F Firm→F, A AOP→P, T Trust→T, B BOI→B,
 * J AJP→J, P Individual→Q, H HUF→Q. Government and local-authority holders (G, L, …)
 * have no supported deductor code.
 */
public final class EntityClass {

    /** The effective deductor code and where it came from; both null when unknown. */
    public record EffectiveCode(String code, String source) {
    }

    private static final String PAN = "PAN";
    private static final String DEDUCTOR_TYPE = "DEDUCTOR_TYPE";

    private static final Map<Character, String> PAN_CLASS_TO_CODE = Map.of(
            'C', "K",
            'F', "F",
            'A', "P",
            'T', "T",
            'B', "B",
            'J', "J",
            'P', "Q",
            'H', "Q");

    private EntityClass() {
    }

    public static Optional<String> deductorCodeFromPan(String pan) {
        if (pan == null || !pan.matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
            return Optional.empty();
        }
        return Optional.ofNullable(PAN_CLASS_TO_CODE.get(pan.charAt(3)));
    }

    /**
     * The stored deductor type wins; otherwise the PAN decides; otherwise unknown.
     */
    public static EffectiveCode effectiveCode(String pan, String deductorType) {
        if (DeductorType.isValid(deductorType)) {
            return new EffectiveCode(deductorType, DEDUCTOR_TYPE);
        }
        return deductorCodeFromPan(pan)
                .map(code -> new EffectiveCode(code, PAN))
                .orElse(new EffectiveCode(null, null));
    }
}
