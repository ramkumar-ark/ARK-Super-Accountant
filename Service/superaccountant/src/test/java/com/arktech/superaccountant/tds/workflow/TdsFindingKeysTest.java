package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.tds.engine.TdsEngineFixtures;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsFindingKeysTest {

    private TdsFinding finding(TdsFindingType type, String section, ParsedVoucher voucher,
                               String party, String expected, String actual) {
        TdsFinding f = new TdsFinding();
        f.setId(UUID.randomUUID());
        f.setOrganizationId(TdsEngineFixtures.ORG);
        f.setWorkflowId(TdsEngineFixtures.WF);
        f.setUploadJobId(TdsEngineFixtures.JOB);
        f.setFindingType(type);
        f.setSeverity(FindingSeverity.MEDIUM);
        f.setPartyLedgerName(party);
        if (voucher != null) {
            f.setVoucherId(voucher.getId());
        }
        f.setVoucherDate(voucher == null ? LocalDate.of(2026, 5, 10) : voucher.getVoucherDate());
        f.setVoucherNumber(voucher == null ? null : voucher.getVoucherNumber());
        f.setSection(section);
        f.setExpectedAmount(expected == null ? null : new BigDecimal(expected));
        f.setActualAmount(actual == null ? null : new BigDecimal(actual));
        return f;
    }

    @Test
    void stamp_voucherFinding_usesGuidIdentity() {
        ParsedVoucher v = TdsEngineFixtures.voucher("V1", LocalDate.of(2026, 5, 10),
                "ABC Contractors", new Object[][]{{"Subcontractor Charges", "100000"}});
        v.setVoucherGuid("g-1");
        v.setContentHash("c" .repeat(64));
        TdsFinding f = finding(TdsFindingType.SHORT_DEDUCTED, "194C", v, "ABC Contractors",
                "1000.00", "500.00");
        Map<UUID, ParsedVoucher> byId = new HashMap<>();
        byId.put(v.getId(), v);

        TdsFindingKeys.stamp(f, byId);

        assertEquals("SHORT_DEDUCTED|g-1|194C", f.getFindingKey());
        assertTrue(f.getEvidenceHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void stamp_sameEvidenceDifferentUpload_sameHash() {
        ParsedVoucher a = TdsEngineFixtures.voucher("V1", LocalDate.of(2026, 5, 10),
                "ABC Contractors", new Object[][]{{"Subcontractor Charges", "100000"}});
        a.setVoucherGuid("g-1");
        a.setContentHash("same-hash");
        ParsedVoucher b = TdsEngineFixtures.voucher("V1", LocalDate.of(2026, 5, 10),
                "ABC Contractors", new Object[][]{{"Subcontractor Charges", "100000"}});
        b.setVoucherGuid("g-1");
        b.setContentHash("same-hash");
        TdsFinding fa = finding(TdsFindingType.SHORT_DEDUCTED, "194C", a, "ABC Contractors",
                "1000.00", "500.00");
        TdsFinding fb = finding(TdsFindingType.SHORT_DEDUCTED, "194C", b, "ABC Contractors",
                "1000.00", "500.00");

        TdsFindingKeys.stamp(fa, Map.of(a.getId(), a));
        TdsFindingKeys.stamp(fb, Map.of(b.getId(), b));

        assertEquals(fa.getFindingKey(), fb.getFindingKey());
        assertEquals(fa.getEvidenceHash(), fb.getEvidenceHash());
    }

    @Test
    void stamp_retroThreshold_usesPartyIdentity() {
        TdsFinding f = finding(TdsFindingType.THRESHOLD_CROSSED, "194C", null, " ABC Ltd ",
                null, null);
        f.setRelatedVoucherIds("[\"v1\",\"v2\"]");

        TdsFindingKeys.stamp(f, Map.of());

        assertEquals("THRESHOLD_CROSSED|party:abc ltd|194C", f.getFindingKey());
        assertTrue(f.getEvidenceHash().matches("[0-9a-f]{64}"));
    }
}
