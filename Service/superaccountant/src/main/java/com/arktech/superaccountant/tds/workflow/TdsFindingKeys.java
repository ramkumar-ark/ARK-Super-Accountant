package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.review.EvidenceHash;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsFinding;

import java.util.Map;
import java.util.UUID;

/** Stamps a TdsFinding with its stored finding key and evidence hash. */
public final class TdsFindingKeys {

    private TdsFindingKeys() {
    }

    /**
     * Identity is the Tally GUID when the finding's voucher resolves, else "party:&lt;key&gt;" (retro
     * threshold findings carry relatedVoucherIds and no single voucher).
     */
    public static void stamp(TdsFinding f, Map<UUID, ParsedVoucher> vouchersById) {
        ParsedVoucher v = f.getVoucherId() == null ? null : vouchersById.get(f.getVoucherId());
        String identity = v != null
                ? EvidenceHash.voucherIdentity(v.getVoucherGuid(), v.getVoucherTypeName(),
                        v.getVoucherNumber(), v.getVoucherDate())
                : "party:" + TdsEngine.key(f.getPartyLedgerName());
        f.setFindingKey(f.getFindingType().name() + "|" + identity + "|"
                + (f.getSection() == null ? "" : f.getSection()));
        f.setEvidenceHash(EvidenceHash.of(f.getFindingType().name(), identity,
                f.getExpectedAmount() == null ? null : f.getExpectedAmount().toPlainString(),
                f.getActualAmount() == null ? null : f.getActualAmount().toPlainString(),
                v != null ? v.getContentHash() : f.getRelatedVoucherIds()));
    }
}
