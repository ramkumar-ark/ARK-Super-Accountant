package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.gst.rules.GstRuleSupport;

import java.math.BigDecimal;

/**
 * Taxable value plus the four tax heads of one document, table row or side of a comparison.
 * Head sums are stored as positive values.
 */
public record HeadAmounts(BigDecimal taxable, BigDecimal igst, BigDecimal cgst, BigDecimal sgst, BigDecimal cess) {

    public static final HeadAmounts ZERO =
            new HeadAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public HeadAmounts {
        taxable = nvl(taxable);
        igst = nvl(igst);
        cgst = nvl(cgst);
        sgst = nvl(sgst);
        cess = nvl(cess);
    }

    public HeadAmounts plus(HeadAmounts other) {
        return new HeadAmounts(
                taxable.add(other.taxable),
                igst.add(other.igst),
                cgst.add(other.cgst),
                sgst.add(other.sgst),
                cess.add(other.cess));
    }

    public HeadAmounts minus(HeadAmounts other) {
        return new HeadAmounts(
                taxable.subtract(other.taxable),
                igst.subtract(other.igst),
                cgst.subtract(other.cgst),
                sgst.subtract(other.sgst),
                cess.subtract(other.cess));
    }

    /** True when any tax head differs by more than the shared tolerance. */
    public boolean taxDiffersFrom(HeadAmounts other) {
        return differs(igst, other.igst) || differs(cgst, other.cgst)
                || differs(sgst, other.sgst) || differs(cess, other.cess);
    }

    /** True when the taxable value differs by more than the shared tolerance. */
    public boolean taxableDiffersFrom(HeadAmounts other) {
        return differs(taxable, other.taxable);
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(GstRuleSupport.TOLERANCE) > 0;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
