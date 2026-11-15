package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure mapping of workflow type to the masters attributes frozen at Finalize
 * (addendum item 1). The GST rate is not frozen: the masters snapshot has no
 * rate field (OQ-Sm-1); state changes surface through the GSTIN attribute.
 */
public final class MasterAttributes {
    private MasterAttributes() {}

    public static final Map<WorkflowType, List<String>> ATTRIBUTES_BY_TYPE = Map.of(
            WorkflowType.GSTR1, List.of("category", "gstin", "gstRegistrationType",
                    "gstApplicable", "gstApplicabilityType", "hsnSacCode"),
            WorkflowType.GSTR3B, List.of("category", "gstin", "gstRegistrationType",
                    "gstApplicable", "gstApplicabilityType", "hsnSacCode"),
            WorkflowType.TDS_PAYMENT, List.of("category", "pan", "tdsSection", "tdsApplicable"),
            WorkflowType.TDS_26Q, List.of("category", "pan", "tdsSection", "tdsApplicable"));

    /** Values are trimmed string forms; blank becomes null; enums use name(); booleans "true"/"false". */
    public static Map<String, String> valuesFor(WorkflowType type, UploadLedgerSnapshot s) {
        Map<String, String> values = new LinkedHashMap<>();
        if (s == null) {
            for (String attribute : ATTRIBUTES_BY_TYPE.get(type)) {
                values.put(attribute, null);
            }
            return values;
        }
        for (String attribute : ATTRIBUTES_BY_TYPE.get(type)) {
            values.put(attribute, valueOf(attribute, s));
        }
        return values;
    }

    private static String valueOf(String attribute, UploadLedgerSnapshot s) {
        Object raw = switch (attribute) {
            case "category" -> s.getCategory() == null ? null : s.getCategory().name();
            case "gstin" -> s.getGstin();
            case "gstRegistrationType" -> s.getGstRegistrationType();
            case "gstApplicable" -> s.getGstApplicable() == null ? null : String.valueOf(s.getGstApplicable());
            case "gstApplicabilityType" -> s.getGstApplicabilityType() == null
                    ? null : s.getGstApplicabilityType().name();
            case "hsnSacCode" -> s.getHsnSacCode();
            case "pan" -> s.getPan();
            case "tdsSection" -> s.getTdsSection();
            case "tdsApplicable" -> s.getTdsApplicable() == null ? null : String.valueOf(s.getTdsApplicable());
            default -> null;
        };
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }
}
