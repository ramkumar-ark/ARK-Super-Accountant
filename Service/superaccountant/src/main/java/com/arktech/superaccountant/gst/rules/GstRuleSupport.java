package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Static helpers shared by all daybook GST rules. */
public final class GstRuleSupport {

    public static final BigDecimal TOLERANCE = BigDecimal.ONE;

    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private GstRuleSupport() {}

    /** Head of the ledger from its saved mapping; null when unmapped or NOT_TAX. */
    public static GstHead gstHead(GstRuleContext ctx, String ledgerName) {
        return ctx.head(ledgerName);
    }

    public static boolean isValidGstin(String gstin) {
        return gstin != null && GSTIN.matcher(gstin.trim().toUpperCase()).matches();
    }

    public static String stateCode(String gstin) {
        return isValidGstin(gstin) ? gstin.trim().substring(0, 2) : null;
    }

    public static boolean isPurchaseSide(String voucherTypeName) {
        return "Purchase".equalsIgnoreCase(voucherTypeName) || "Debit Note".equalsIgnoreCase(voucherTypeName);
    }

    public static boolean isSalesSide(String voucherTypeName) {
        return "Sales".equalsIgnoreCase(voucherTypeName) || "Credit Note".equalsIgnoreCase(voucherTypeName);
    }

    /** SALES/PURCHASE from the voucher type; OTHER when the type is neither. */
    public static VoucherSide side(ParsedVoucher voucher) {
        if (voucher == null || voucher.getVoucherTypeName() == null) return VoucherSide.OTHER;
        if (isSalesSide(voucher.getVoucherTypeName())) return VoucherSide.SALES;
        if (isPurchaseSide(voucher.getVoucherTypeName())) return VoucherSide.PURCHASE;
        return VoucherSide.OTHER;
    }

    /** Entries whose saved mapping resolves to a voucher tax role (output/input/RCM); balance, TDS and NOT_TAX roles excluded. */
    public static List<ParsedVoucherEntry> gstEntries(ParsedVoucher voucher, GstRuleContext ctx) {
        if (voucher.getEntries() == null) return List.of();
        return voucher.getEntries().stream()
                .filter(e -> {
                    TaxLedgerRole role = ctx.role(e.getLedgerName());
                    return role != null && role.isVoucherTax();
                })
                .toList();
    }

    /** Sum of abs(amount) per mapped head over the given entries, cess included. */
    public static Map<GstHead, BigDecimal> headTotals(GstRuleContext ctx, List<ParsedVoucherEntry> entries) {
        return headTotals(ctx, entries, true);
    }

    /** Same as headTotals but without CESS — use for rate comparisons. */
    public static Map<GstHead, BigDecimal> rateHeadTotals(GstRuleContext ctx, List<ParsedVoucherEntry> entries) {
        return headTotals(ctx, entries, false);
    }

    private static Map<GstHead, BigDecimal> headTotals(GstRuleContext ctx, List<ParsedVoucherEntry> entries,
                                                       boolean includeCess) {
        Map<GstHead, BigDecimal> totals = new HashMap<>();
        for (ParsedVoucherEntry e : entries) {
            if (e.getAmount() == null) continue;
            GstHead head = gstHead(ctx, e.getLedgerName());
            if (head == null || (head == GstHead.CESS && !includeCess)) continue;
            totals.merge(head, e.getAmount().abs(), BigDecimal::add);
        }
        return totals;
    }

    /** TRUE = inter-state, FALSE = intra-state, null = undeterminable (missing/invalid GSTIN either side). */
    public static Boolean interState(GstRuleContext ctx, ParsedVoucher voucher) {
        String orgState = ctx.orgStateCode();
        String partyState = stateCode(voucher.getPartyGstin());
        if (orgState == null || partyState == null) return null;
        return !orgState.equals(partyState);
    }

    public static GstDaybookFinding finding(GstRuleContext ctx, ParsedVoucher v, GstFindingType type,
                                            FindingSeverity severity, String ledgerName,
                                            String expected, String actual, String message) {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setOrganizationId(ctx.organizationId());
        f.setWorkflowId(ctx.workflowId());
        f.setUploadJobId(ctx.uploadJobId());
        f.setVoucherSide(side(v));
        f.setFindingType(type);
        f.setSeverity(severity);
        f.setVoucherId(v.getId());
        f.setVoucherGuid(v.getVoucherGuid());
        f.setVoucherDate(v.getVoucherDate());
        f.setVoucherNumber(v.getVoucherNumber());
        f.setVoucherTypeName(v.getVoucherTypeName());
        f.setPartyLedgerName(v.getPartyLedgerName());
        f.setLedgerName(ledgerName);
        f.setExpectedValue(expected);
        f.setActualValue(actual);
        f.setMessage(message);
        return f;
    }
}
