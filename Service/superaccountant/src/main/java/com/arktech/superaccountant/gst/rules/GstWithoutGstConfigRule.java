package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GstWithoutGstConfigRule implements GstDaybookRule {

    private static final Set<GstApplicabilityType> NON_TAXABLE_TYPES = Set.of(
            GstApplicabilityType.EXEMPT, GstApplicabilityType.NON_GST, GstApplicabilityType.NOT_APPLICABLE);

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        if (vouchers == null) return findings;
        for (ParsedVoucher v : vouchers) {
            if (v == null || v.isCancelled() || v.getEntries() == null) continue;
            if (GstRuleSupport.gstEntries(v, ctx).stream()
                    .noneMatch(e -> e.getAmount() != null && e.getAmount().compareTo(BigDecimal.ZERO) != 0)) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (e.isPartyLedger()) continue;
                if (!e.isDebit()) continue;
                LedgerCategory category = ctx.category(e.getLedgerName());
                if (category != LedgerCategory.EXPENSE && category != LedgerCategory.PURCHASE) continue;
                UploadLedgerSnapshot snapshot = ctx.snapshot(e.getLedgerName()).orElse(null);
                if (snapshot == null) continue;
                String key = e.getLedgerName().trim().toLowerCase();
                if (!seen.add(key)) continue;
                Boolean flag = snapshot.getGstApplicable();
                GstApplicabilityType type = snapshot.getGstApplicabilityType();
                boolean misconfigured = !Boolean.TRUE.equals(flag)
                        || (type != null && NON_TAXABLE_TYPES.contains(type));
                if (!misconfigured) continue;
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.GST_WITHOUT_GST_CONFIG,
                        FindingSeverity.MEDIUM, e.getLedgerName(),
                        "gstApplicable=true with a taxable GST type",
                        "gstApplicable=" + flag + ", type=" + type,
                        "Voucher carries GST but expense ledger '" + e.getLedgerName()
                                + "' is not configured as GST-applicable in masters"));
            }
        }
        return findings;
    }
}
