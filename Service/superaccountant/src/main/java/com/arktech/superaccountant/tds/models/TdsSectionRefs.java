package com.arktech.superaccountant.tds.models;

import java.time.LocalDate;
import java.util.Map;

/**
 * The single backend source of Income-tax Act 2025 s.393 display references for TDS sections.
 * Internal section codes stay the stored key everywhere; these strings are for humans only.
 * Mirrors the client's {@code tdsSections393.ts} map, which cannot read the database.
 */
public final class TdsSectionRefs {

    private static final Map<String, String> NEW_ACT_REFS = Map.of(
            "194A", "393(1) [Table: Sl. No. 5(ii) and 5(iii)]",
            "194C", "393(1) [Table: Sl. No. 6(i)]",
            "194H", "393(1) [Table: Sl. No. 1(ii)]",
            "194I", "393(1) [Table: Sl. No. 2(ii).D(b)]",
            "194J_A", "393(1) [Table: Sl. No. 6(iii).D(a)]",
            "194J_B", "393(1) [Table: Sl. No. 6(iii).D(b)]",
            "194Q", "393(1) [Table: Sl. No. 8(ii)]");

    private TdsSectionRefs() {
    }

    /** The s.393 reference for a section, or null when the section has no new-Act mapping. */
    public static String newActRef(String sectionCode) {
        return sectionCode == null ? null : NEW_ACT_REFS.get(sectionCode);
    }

    /**
     * The reference to show for a known TDS date: the s.393 reference on/after 1 April 2026,
     * otherwise the old section code. Null dates fall back to the code.
     */
    public static String forDate(String sectionCode, LocalDate tdsDate) {
        if (sectionCode == null) {
            return null;
        }
        String ref = NEW_ACT_REFS.get(sectionCode);
        return ref != null && tdsDate != null && TdsSectionConfig.newActApplies(tdsDate) ? ref : sectionCode;
    }

    /** Old section first, then the s.393 reference, for places where no period is known. */
    public static String both(String sectionCode) {
        if (sectionCode == null) {
            return null;
        }
        String ref = NEW_ACT_REFS.get(sectionCode);
        return ref == null ? sectionCode : sectionCode + " / " + ref;
    }
}
