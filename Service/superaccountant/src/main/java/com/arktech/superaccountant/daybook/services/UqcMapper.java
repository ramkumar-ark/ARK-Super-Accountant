package com.arktech.superaccountant.daybook.services;

import java.util.Map;

/**
 * Maps a Tally unit symbol to the GST UQC code list (Notification 1/2017
 * statutory units). Known symbols map to their official UQC code; unknown
 * non-blank symbols are uppercased and passed through so the data stays
 * visible in the HSN summary; blank input maps to null (row falls back to NA).
 */
public final class UqcMapper {

    private static final Map<String, String> UQC_BY_UNIT = Map.ofEntries(
            Map.entry("BAG", "BAG"), Map.entry("BDL", "BDL"), Map.entry("BKL", "BKL"),
            Map.entry("BOB", "BOB"), Map.entry("BOL", "BOL"), Map.entry("BOX", "BOX"),
            Map.entry("BTL", "BTL"), Map.entry("BUN", "BUN"), Map.entry("CAN", "CAN"),
            Map.entry("CBM", "CBM"), Map.entry("CCM", "CCM"), Map.entry("CMS", "CMS"),
            Map.entry("CTN", "CTN"), Map.entry("DOZ", "DOZ"), Map.entry("DRM", "DRM"),
            Map.entry("GGK", "GGK"), Map.entry("GMS", "GMS"), Map.entry("GRS", "GRS"),
            Map.entry("GYD", "GYD"), Map.entry("KGS", "KGS"), Map.entry("KLR", "KLR"),
            Map.entry("KME", "KME"), Map.entry("LTR", "LTR"), Map.entry("MGS", "MGS"),
            Map.entry("MLS", "MLS"), Map.entry("MMT", "MMT"), Map.entry("MTR", "MTR"),
            Map.entry("MTS", "MTS"), Map.entry("NOS", "NOS"), Map.entry("PAC", "PAC"),
            Map.entry("PCS", "PCS"), Map.entry("PRS", "PRS"), Map.entry("QTL", "QTL"),
            Map.entry("ROL", "ROL"), Map.entry("SET", "SET"), Map.entry("SQF", "SQF"),
            Map.entry("SQM", "SQM"), Map.entry("SQY", "SQY"), Map.entry("TBS", "TBS"),
            Map.entry("TGM", "TGM"), Map.entry("THD", "THD"), Map.entry("TON", "TON"),
            Map.entry("TUB", "TUB"), Map.entry("UGS", "UGS"), Map.entry("UNT", "UNT"),
            Map.entry("YDS", "YDS"),
            // Common Tally symbols not equal to their UQC code
            Map.entry("KG", "KGS"), Map.entry("KGS.", "KGS"),
            Map.entry("M", "MTR"), Map.entry("MTRS", "MTR"), Map.entry("METERS", "MTR"),
            Map.entry("L", "LTR"), Map.entry("LTRS", "LTR"), Map.entry("LITRE", "LTR"),
            Map.entry("LITRES", "LTR"), Map.entry("NO", "NOS"), Map.entry("NOS.", "NOS"),
            Map.entry("NUM", "NOS"), Map.entry("NUMBERS", "NOS"), Map.entry("PC", "PCS"),
            Map.entry("PCS.", "PCS"), Map.entry("PIECES", "PCS"), Map.entry("PCE", "PCS"),
            Map.entry("SQFT", "SQF"), Map.entry("SQ.FT", "SQF"), Map.entry("SQ FEET", "SQF"),
            Map.entry("SQMT", "SQM"), Map.entry("SQ.MT", "SQM"), Map.entry("SQ METER", "SQM"),
            Map.entry("MT", "MTS"), Map.entry("TONNES", "MTS"), Map.entry("TONNE", "MTS"),
            Map.entry("G", "GMS"), Map.entry("GM", "GMS"), Map.entry("GRAM", "GMS"),
            Map.entry("GRAMS", "GMS"), Map.entry("ML", "MLS"), Map.entry("MILLILITRE", "MLS"),
            Map.entry("MILLILITRES", "MLS"), Map.entry("MM", "MMT"), Map.entry("CMS.", "CMS"),
            Map.entry("CM", "CMS"), Map.entry("CENTIMETER", "CMS"), Map.entry("PAIR", "PRS"),
            Map.entry("PAIRS", "PRS"), Map.entry("DZN", "DOZ"), Map.entry("DOZEN", "DOZ"),
            Map.entry("BOTTLES", "BTL"), Map.entry("BOTTLE", "BTL"), Map.entry("CTNS", "CTN"),
            Map.entry("CARTON", "CTN"), Map.entry("CARTONS", "CTN"), Map.entry("SETS", "SET"),
            Map.entry("ROLL", "ROL"), Map.entry("ROLLS", "ROL"), Map.entry("DRUM", "DRM"),
            Map.entry("DRUMS", "DRM"), Map.entry("BAGS", "BAG"), Map.entry("BOXES", "BOX"),
            Map.entry("BUNDLES", "BDL"), Map.entry("CANISTER", "CAN"), Map.entry("CANISTERS", "CAN"),
            Map.entry("CUBIC METER", "CBM"), Map.entry("CUBIC METERS", "CBM"),
            Map.entry("CUBIC CM", "CCM"), Map.entry("GROSS", "GRS"), Map.entry("KILOMETRE", "KLR"),
            Map.entry("KILOMETRES", "KLR"), Map.entry("KILOMETER", "KLR"), Map.entry("TUBES", "TUB"));

    private UqcMapper() {
    }

    /**
     * @return the GST UQC code for the given Tally unit symbol, the uppercased
     *         symbol itself when unknown, or null for blank input.
     */
    public static String map(String tallyUnit) {
        if (tallyUnit == null || tallyUnit.isBlank()) {
            return null;
        }
        String key = tallyUnit.trim().toUpperCase();
        return UQC_BY_UNIT.getOrDefault(key, key);
    }
}
