package com.arktech.superaccountant.masters.scope;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure scope map: which workflow types a masters finding affects (spec rules 5 and 6).
 * Rule 5: a ledger whose category changed between uploads stays in scope for both the
 * old and the new category. Rule 6: unmapped findings are scoped by category.
 */
public final class MastersScope {
    private MastersScope() {}

    private static final Set<WorkflowType> GSTR1_3B = Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B);
    private static final Set<WorkflowType> GSTR3B = Set.of(WorkflowType.GSTR3B);
    private static final Set<WorkflowType> TDS = Set.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q);

    /** latest/previous are keyed by {@link LedgerNames#normalize(ledgerName)}; either may be empty, never null. */
    public static Set<WorkflowType> typesFor(ValidationFinding f,
                                             Map<String, UploadLedgerSnapshot> latest,
                                             Map<String, UploadLedgerSnapshot> previous) {
        UploadLedgerSnapshot l = latest.get(LedgerNames.normalize(f.getLedgerName()));
        UploadLedgerSnapshot p = previous.get(LedgerNames.normalize(f.getLedgerName()));

        Set<WorkflowType> base = EnumSet.noneOf(WorkflowType.class);
        addCategoryTypes(base, f.getCategory(), ledgerName(f, l, p), l, p);
        if (l != null && l.getCategory() != null) addCategoryTypes(base, l.getCategory(), ledgerName(f, l, p), l, p);
        if (p != null && p.getCategory() != null) addCategoryTypes(base, p.getCategory(), ledgerName(f, l, p), l, p);

        String rule = f.getRuleCode() == null ? "" : f.getRuleCode();
        return switch (rule) {
            case "GSTIN_PRESENCE" -> gstinPresence(base, f, l, p);
            case "GST_APPLICABILITY", "HSN_SAC_CODE" -> intersect(base, GSTR1_3B);
            case "TDS_SECTION_MAPPING" -> EnumSet.copyOf(TDS);
            default -> base; // MISMATCH_DETECTION and unknown codes: rule 6
        };
    }

    private static Set<WorkflowType> gstinPresence(Set<WorkflowType> base, ValidationFinding f,
                                                   UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        String chain = chainOf(l, p);
        if (chain.contains("sundry debtors")) {
            return EnumSet.copyOf(GSTR1_3B);
        }
        if (f.getCategory() == LedgerCategory.CREDITOR || chain.contains("sundry creditors")) {
            Set<WorkflowType> out = EnumSet.copyOf(base);
            out.add(WorkflowType.GSTR3B);
            return out;
        }
        return intersect(base, GSTR1_3B);
    }

    private static void addCategoryTypes(Set<WorkflowType> out, LedgerCategory category,
                                         String ledgerName, UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        switch (category) {
            case INCOME -> out.addAll(GSTR1_3B);
            case GST -> out.addAll(gstCategoryTypes(ledgerName));
            case PURCHASE -> out.addAll(GSTR3B);
            case EXPENSE, CREDITOR -> {
                out.addAll(GSTR3B);
                if (tdsMapped(l, p)) out.addAll(TDS);
            }
            case TDS -> out.addAll(TDS);
            case OTHER -> {}
        }
    }

    private static Set<WorkflowType> gstCategoryTypes(String ledgerName) {
        String name = ledgerName == null ? "" : ledgerName.toLowerCase(Locale.ROOT);
        if (name.contains("output")) return GSTR1_3B;
        if (name.contains("input")) return GSTR3B;
        return GSTR1_3B; // OQ-M4: neither → both
    }

    private static boolean tdsMapped(UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        return hasTds(l) || hasTds(p);
    }

    private static boolean hasTds(UploadLedgerSnapshot s) {
        return s != null && ((s.getTdsSection() != null && !s.getTdsSection().isBlank())
                || Boolean.TRUE.equals(s.getTdsApplicable()));
    }

    private static String chainOf(UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        String chain = l != null && l.getGroupChain() != null ? l.getGroupChain()
                : p != null ? p.getGroupChain() : null;
        return chain == null ? "" : chain.toLowerCase(Locale.ROOT);
    }

    private static String ledgerName(ValidationFinding f, UploadLedgerSnapshot l, UploadLedgerSnapshot p) {
        if (l != null && l.getLedgerName() != null) return l.getLedgerName();
        if (p != null && p.getLedgerName() != null) return p.getLedgerName();
        return f.getLedgerName();
    }

    private static Set<WorkflowType> intersect(Set<WorkflowType> base, Set<WorkflowType> allowed) {
        Set<WorkflowType> out = EnumSet.copyOf(base);
        out.retainAll(allowed);
        return out;
    }
}
