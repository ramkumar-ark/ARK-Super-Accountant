package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
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

    /** IGST checked before CGST/SGST — "IGST" contains no other head token. */
    public static String gstHead(String ledgerName) {
        if (ledgerName == null) return null;
        String upper = ledgerName.toUpperCase();
        if (upper.contains("IGST")) return "IGST";
        if (upper.contains("CGST")) return "CGST";
        if (upper.contains("SGST")) return "SGST";
        return null;
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

    /** Entries whose snapshot category is GST and whose name resolves to a head. */
    public static List<ParsedVoucherEntry> gstEntries(ParsedVoucher voucher, GstRuleContext ctx) {
        if (voucher.getEntries() == null) return List.of();
        return voucher.getEntries().stream()
                .filter(e -> ctx.category(e.getLedgerName()) == com.arktech.superaccountant.masters.models.LedgerCategory.GST)
                .filter(e -> gstHead(e.getLedgerName()) != null)
                .toList();
    }

    /** Sum of abs(amount) per head over the given entries. Missing heads absent from the map. */
    public static Map<String, BigDecimal> headTotals(List<ParsedVoucherEntry> entries) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (ParsedVoucherEntry e : entries) {
            String head = gstHead(e.getLedgerName());
            if (head == null || e.getAmount() == null) continue;
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
