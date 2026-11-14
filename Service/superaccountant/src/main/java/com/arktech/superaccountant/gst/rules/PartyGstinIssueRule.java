package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.rules.GstRegistrationTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule 8 — PARTY_GSTIN_ISSUE (MEDIUM): purchase-side vouchers for registered parties
 * with a missing, malformed or masters-mismatched party GSTIN. Decided by the party's
 * GST registration type (voucher value first, ledger snapshot fallback); unregistered
 * and blank-typed parties stay silent.
 */
@Component
public class PartyGstinIssueRule implements GstDaybookRule {

    @Override
    public List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers) {
        List<GstDaybookFinding> findings = new ArrayList<>();
        for (ParsedVoucher v : vouchers) {
            if (v.isCancelled() || !GstRuleSupport.isPurchaseSide(v.getVoucherTypeName())) continue;

            String type = v.getPartyGstRegistrationType();
            if (type == null || type.isBlank()) {
                UploadLedgerSnapshot party = ctx.snapshot(v.getPartyLedgerName()).orElse(null);
                type = party == null ? null : party.getGstRegistrationType();
            }
            if (type == null || type.isBlank()) continue;
            if (!GstRegistrationTypes.isRegistered(type)) continue;

            String partyName = v.getPartyLedgerName();
            String voucherGstin = v.getPartyGstin() == null || v.getPartyGstin().isBlank()
                    ? null : v.getPartyGstin();
            String mastersGstin = ctx.snapshot(v.getPartyLedgerName())
                    .map(s -> s.getGstin() == null || s.getGstin().isBlank() ? null : s.getGstin())
                    .orElse(null);

            if (voucherGstin == null) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.PARTY_GSTIN_ISSUE,
                        FindingSeverity.MEDIUM, partyName,
                        mastersGstin != null ? mastersGstin : "GSTIN for registered party",
                        "",
                        "Registered party '" + partyName + "' has no GSTIN on the voucher"));
                continue;
            }

            if (!GstRuleSupport.isValidGstin(voucherGstin)) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.PARTY_GSTIN_ISSUE,
                        FindingSeverity.MEDIUM, partyName,
                        "15-char GSTIN like 27AAPFU0939F1ZV",
                        voucherGstin,
                        "Party GSTIN on voucher has invalid format"));
                continue;
            }

            if (mastersGstin != null
                    && !voucherGstin.trim().equalsIgnoreCase(mastersGstin.trim())) {
                findings.add(GstRuleSupport.finding(ctx, v, GstFindingType.PARTY_GSTIN_ISSUE,
                        FindingSeverity.MEDIUM, partyName,
                        mastersGstin,
                        voucherGstin,
                        "Voucher party GSTIN differs from masters GSTIN for '" + partyName + "'"));
            }
        }
        return findings;
    }
}
