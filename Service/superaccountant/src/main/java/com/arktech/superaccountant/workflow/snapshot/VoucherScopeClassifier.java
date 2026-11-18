package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which workflow types a voucher change can affect, from the voucher
 * itself and the validated ledger snapshots (read directly, not via MastersJobGate).
 */
@Component
public class VoucherScopeClassifier {

    public Set<WorkflowType> scopes(VoucherPayload voucher, Map<String, UploadLedgerSnapshot> ledgersByName) {
        Set<WorkflowType> scopes = EnumSet.noneOf(WorkflowType.class);
        boolean salesLike = GstRuleSupport.isSalesSide(voucher.type());
        boolean purchaseLike = GstRuleSupport.isPurchaseSide(voucher.type());
        boolean tds = false;

        for (VoucherPayload.Entry entry : voucher.entries()) {
            UploadLedgerSnapshot ledger = ledgersByName.get(entry.ledger());
            if (ledger == null) {
                continue;
            }
            if (ledger.getCategory() == LedgerCategory.GST) {
                boolean output = ledger.getLedgerName() != null
                        && ledger.getLedgerName().toUpperCase(Locale.ROOT).contains("OUTPUT");
                salesLike |= output;
                purchaseLike = true;
            }
            if (ledger.getCategory() == LedgerCategory.TDS
                    || (ledger.getTdsSection() != null && !ledger.getTdsSection().isBlank())) {
                tds = true;
            }
        }

        if (salesLike) {
            scopes.add(WorkflowType.GSTR1);
            scopes.add(WorkflowType.GSTR3B);
            scopes.add(WorkflowType.IFF);
        } else if (purchaseLike) {
            scopes.add(WorkflowType.GSTR3B);
        }
        if (tds) {
            scopes.add(WorkflowType.TDS_PAYMENT);
            scopes.add(WorkflowType.TDS_26Q);
        }
        return scopes;
    }

    public static String touchedScopesText(Set<WorkflowType> scopes) {
        return scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    /** Inverse of {@link #touchedScopesText}: parses the comma-joined text back to names. */
    public static java.util.List<String> touchedScopesList(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
