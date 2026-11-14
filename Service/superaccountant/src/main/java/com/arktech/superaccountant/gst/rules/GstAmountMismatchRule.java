package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.gst.rules.GstRuleSupport.TOLERANCE;

/**
 * Rule 2 — GST_AMOUNT_MISMATCH (HIGH): actual GST vs taxable base x persisted gstRate.
 * Intra-state: half rate each to CGST/SGST; inter-state: full rate to IGST;
 * undeterminable party state: total-vs-total. Skips vouchers with no rate data.
 */
@Component
public class GstAmountMismatchRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || v.getEntries() == null) continue;

            List<ParsedVoucherEntry> gstEntries = GstRuleSupport.gstEntries(v, ctx);
            boolean hasGst = gstEntries.stream()
                    .anyMatch(e -> e.getAmount() != null && e.getAmount().compareTo(BigDecimal.ZERO) != 0);
            if (!hasGst) continue;

            BigDecimal expectedTotal = BigDecimal.ZERO;
            boolean anyRate = false;
            for (ParsedVoucherEntry e : v.getEntries()) {
                if (e.getGstRate() != null && e.getGstRate().compareTo(BigDecimal.ZERO) > 0
                        && e.getAmount() != null) {
                    anyRate = true;
                    expectedTotal = expectedTotal.add(
                            e.getAmount().abs().multiply(e.getGstRate())
                                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
                }
            }
            if (!anyRate) continue;

            Map<String, BigDecimal> heads = GstRuleSupport.headTotals(gstEntries);
            BigDecimal cgst = heads.getOrDefault("CGST", BigDecimal.ZERO);
            BigDecimal sgst = heads.getOrDefault("SGST", BigDecimal.ZERO);
            BigDecimal igst = heads.getOrDefault("IGST", BigDecimal.ZERO);
            BigDecimal actualTotal = cgst.add(sgst).add(igst);

            Boolean inter = GstRuleSupport.interState(ctx, v);
            boolean mismatch;
            if (Boolean.TRUE.equals(inter) && igst.compareTo(BigDecimal.ZERO) > 0) {
                mismatch = beyondTolerance(igst, expectedTotal);
            } else if (Boolean.FALSE.equals(inter)
                    && (cgst.compareTo(BigDecimal.ZERO) > 0 || sgst.compareTo(BigDecimal.ZERO) > 0)) {
                BigDecimal half = expectedTotal.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                mismatch = beyondTolerance(cgst, half) || beyondTolerance(sgst, half);
            } else {
                mismatch = beyondTolerance(actualTotal, expectedTotal);
            }

            if (mismatch) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.GST_AMOUNT_MISMATCH,
                        FindingSeverity.HIGH, null,
                        expectedTotal.toPlainString(), actualTotal.toPlainString(),
                        "GST booked " + actualTotal.toPlainString() + " but taxable base x rate expects "
                                + expectedTotal.toPlainString()));
            }
        }
        return findings;
    }

    private boolean beyondTolerance(BigDecimal actual, BigDecimal expected) {
        return actual.subtract(expected).abs().compareTo(TOLERANCE) > 0;
    }
}
