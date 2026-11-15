package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.rules.GstDaybookRule;
import com.arktech.superaccountant.gst.rules.GstRuleContext;
import com.arktech.superaccountant.review.EvidenceHash;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Period-scoped entry point for day-book GST rules. Filters cancelled/out-of-period vouchers,
 * runs every injected rule on the filtered list, stamps the evidence hash (including the
 * source voucher's content hash) and keeps only the sides the caller wants.
 */
@Component
public class GstRuleRunner {

    private final List<GstDaybookRule> rules;

    public GstRuleRunner(List<GstDaybookRule> rules) {
        this.rules = rules;
    }

    public List<GstDaybookFinding> run(GstRuleContext ctx, List<ParsedVoucher> vouchers,
                                       LocalDate periodStart, LocalDate periodEnd,
                                       Set<VoucherSide> keepSides) {
        List<ParsedVoucher> inScope = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || v.getVoucherDate() == null) continue;
            if (v.getVoucherDate().isBefore(periodStart) || v.getVoucherDate().isAfter(periodEnd)) continue;
            inScope.add(v);
        }

        Map<UUID, ParsedVoucher> byId = new HashMap<>();
        for (ParsedVoucher v : inScope) {
            byId.put(v.getId(), v);
        }

        List<GstDaybookFinding> results = new ArrayList<>();
        for (GstDaybookRule rule : rules) {
            for (GstDaybookFinding f : rule.execute(ctx, inScope)) {
                stampEvidence(f, byId.get(f.getVoucherId()));
                if (f.getVoucherSide() != null && keepSides.contains(f.getVoucherSide())) {
                    results.add(f);
                }
            }
        }
        return results;
    }

    private void stampEvidence(GstDaybookFinding f, ParsedVoucher voucher) {
        String voucherIdentity = EvidenceHash.voucherIdentity(f.getVoucherGuid(), f.getVoucherTypeName(),
                f.getVoucherNumber(), f.getVoucherDate());
        String contentHash = voucher == null ? null : voucher.getContentHash();
        f.setEvidenceHash(EvidenceHash.of(f.getFindingType().name(), voucherIdentity,
                f.getExpectedValue(), f.getActualValue(), contentHash));
    }
}
