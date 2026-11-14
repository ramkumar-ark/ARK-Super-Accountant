package com.arktech.superaccountant.gst;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.rules.GstRuleContext;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared builders for GST rule tests. All rule tests use these — do not duplicate builders. */
public final class GstTestFixtures {

    public static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private GstTestFixtures() {}

    public static ParsedVoucher voucher(String type, LocalDate date, String party, String partyGstin) {
        ParsedVoucher v = new ParsedVoucher();
        v.setId(UUID.randomUUID());
        v.setOrganizationId(ORG_ID);
        v.setUploadJobId(JOB_ID);
        v.setVoucherTypeName(type);
        v.setVoucherNumber("V-" + UUID.randomUUID().toString().substring(0, 8));
        v.setVoucherGuid(UUID.randomUUID().toString());
        v.setVoucherDate(date);
        v.setPartyLedgerName(party);
        v.setPartyGstin(partyGstin);
        v.setEntries(new ArrayList<>());
        return v;
    }

    public static ParsedVoucherEntry entry(ParsedVoucher v, String ledger, String amount) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setOrganizationId(ORG_ID);
        e.setLedgerName(ledger);
        BigDecimal amt = new BigDecimal(amount);
        e.setAmount(amt);
        e.setDebit(amt.compareTo(BigDecimal.ZERO) > 0);
        v.getEntries().add(e);
        return e;
    }

    public static ParsedVoucherEntry entryWithRate(ParsedVoucher v, String ledger, String amount,
                                                   String rate, String taxability) {
        ParsedVoucherEntry e = entry(v, ledger, amount);
        e.setGstRate(rate == null ? null : new BigDecimal(rate));
        e.setGstTaxability(taxability);
        return e;
    }

    public static UploadLedgerSnapshot snapshot(String name, LedgerCategory cat) {
        return snapshotGst(name, cat, null, null, null);
    }

    public static UploadLedgerSnapshot snapshotGst(String name, LedgerCategory cat, Boolean gstApplicable,
                                                   GstApplicabilityType type, String gstin) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setCategory(cat);
        s.setGstApplicable(gstApplicable);
        s.setGstApplicabilityType(type);
        s.setGstin(gstin);
        return s;
    }

    public static GstRuleContext ctx(String orgGstin, UploadLedgerSnapshot... snaps) {
        Map<String, UploadLedgerSnapshot> byName = new HashMap<>();
        for (UploadLedgerSnapshot s : snaps) {
            byName.put(s.getLedgerName().trim().toLowerCase(), s);
        }
        return new GstRuleContext(ORG_ID, UUID.randomUUID(), UUID.randomUUID(), orgGstin, byName);
    }
}
