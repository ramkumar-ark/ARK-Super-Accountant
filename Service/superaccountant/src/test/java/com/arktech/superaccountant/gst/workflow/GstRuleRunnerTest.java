package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.GstTestFixtures;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.rules.GstDaybookRule;
import com.arktech.superaccountant.gst.rules.GstRuleContext;
import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.review.EvidenceHash;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GstRuleRunnerTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 4, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 4, 30);

    private final GstRuleContext ctx = GstTestFixtures.ctx(null);

    @Test
    void run_skipsVouchersOutsidePeriodAndNullDates() {
        ParsedVoucher before = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 3, 31), "Acme", "27AAACA1234A1Z5");
        ParsedVoucher inside = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 4, 15), "Acme", "27AAACA1234A1Z5");
        ParsedVoucher after = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 5, 1), "Acme", "27AAACA1234A1Z5");
        ParsedVoucher noDate = GstTestFixtures.voucher("Purchase", null, "Acme", "27AAACA1234A1Z5");

        List<ParsedVoucher> seen = new ArrayList<>();
        GstRuleRunner runner = new GstRuleRunner(List.of(recording(seen)));

        runner.run(ctx, List.of(before, inside, after, noDate), PERIOD_START, PERIOD_END,
                EnumSet.allOf(VoucherSide.class));

        assertThat(seen).containsExactly(inside);
    }

    @Test
    void run_skipsCancelledVouchers() {
        ParsedVoucher cancelled = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 4, 10), "Acme", "27AAACA1234A1Z5");
        cancelled.setCancelled(true);
        ParsedVoucher live = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 4, 10), "Acme", "27AAACA1234A1Z5");

        List<ParsedVoucher> seen = new ArrayList<>();
        GstRuleRunner runner = new GstRuleRunner(List.of(recording(seen)));

        runner.run(ctx, List.of(cancelled, live), PERIOD_START, PERIOD_END, EnumSet.allOf(VoucherSide.class));

        assertThat(seen).containsExactly(live);
    }

    @Test
    void run_keepsOnlyRequestedSides() {
        GstDaybookFinding sales = findingOnSide(VoucherSide.SALES);
        GstDaybookFinding purchase = findingOnSide(VoucherSide.PURCHASE);
        GstDaybookFinding other = findingOnSide(VoucherSide.OTHER);
        GstRuleRunner runner = new GstRuleRunner(List.of((c, v) -> List.of(sales, purchase, other)));

        assertThat(runner.run(ctx, List.of(), PERIOD_START, PERIOD_END, Set.of(VoucherSide.SALES)))
                .containsExactly(sales);
        assertThat(runner.run(ctx, List.of(), PERIOD_START, PERIOD_END, Set.of(VoucherSide.PURCHASE, VoucherSide.OTHER)))
                .containsExactly(purchase, other);
    }

    @Test
    void run_setsEvidenceHashFromVoucherContentHash() {
        ParsedVoucher v = GstTestFixtures.voucher("Purchase", LocalDate.of(2025, 4, 15), "Acme", "27AAACA1234A1Z5");
        v.setContentHash("a".repeat(64));
        GstRuleRunner runner = new GstRuleRunner(List.of(supportFindingRule()));

        List<GstDaybookFinding> first = runner.run(ctx, List.of(v), PERIOD_START, PERIOD_END,
                EnumSet.allOf(VoucherSide.class));

        assertThat(first).hasSize(1);
        String identity = EvidenceHash.voucherIdentity(v.getVoucherGuid(), v.getVoucherTypeName(),
                v.getVoucherNumber(), v.getVoucherDate());
        String expected = EvidenceHash.of(GstFindingType.GST_NOT_CHARGED.name(), identity,
                "expected", "actual", v.getContentHash());
        assertThat(first.get(0).getEvidenceHash()).isEqualTo(expected).hasSize(64)
                .matches("[0-9a-f]{64}");

        v.setContentHash("b".repeat(64));
        List<GstDaybookFinding> second = runner.run(ctx, List.of(v), PERIOD_START, PERIOD_END,
                EnumSet.allOf(VoucherSide.class));
        assertThat(second.get(0).getEvidenceHash()).isNotEqualTo(first.get(0).getEvidenceHash());
    }

    @Test
    void findingKey_includesTypeIdentityAndLedger() {
        GstDaybookFinding withGuid = new GstDaybookFinding();
        withGuid.setVoucherGuid("some-guid");
        withGuid.setLedgerName("Rent");
        withGuid.setFindingType(GstFindingType.GST_NOT_CHARGED);
        assertThat(withGuid.getFindingKey()).isEqualTo("GST_NOT_CHARGED|some-guid|Rent");

        GstDaybookFinding noGuid = new GstDaybookFinding();
        noGuid.setVoucherGuid(null);
        noGuid.setVoucherTypeName("Purchase");
        noGuid.setVoucherNumber("P-1");
        noGuid.setVoucherDate(LocalDate.of(2025, 4, 15));
        noGuid.setLedgerName("Rent");
        noGuid.setFindingType(GstFindingType.GST_NOT_CHARGED);
        assertThat(noGuid.getFindingKey()).isEqualTo("GST_NOT_CHARGED|Purchase|P-1|2025-04-15|Rent");

        GstDaybookFinding noLedger = new GstDaybookFinding();
        noLedger.setVoucherGuid("some-guid");
        noLedger.setLedgerName(null);
        noLedger.setFindingType(GstFindingType.GST_NOT_CHARGED);
        assertThat(noLedger.getFindingKey()).isEqualTo("GST_NOT_CHARGED|some-guid|");
    }

    private GstDaybookRule recording(List<ParsedVoucher> seen) {
        return (c, vouchers) -> {
            seen.addAll(vouchers);
            return List.of();
        };
    }

    private GstDaybookRule supportFindingRule() {
        return (c, vouchers) -> {
            List<GstDaybookFinding> out = new ArrayList<>();
            for (ParsedVoucher v : vouchers) {
                out.add(GstRuleSupport.finding(c, v, GstFindingType.GST_NOT_CHARGED, FindingSeverity.HIGH,
                        "Rent", "expected", "actual", "msg"));
            }
            return out;
        };
    }

    private GstDaybookFinding findingOnSide(VoucherSide side) {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setFindingType(GstFindingType.GST_NOT_CHARGED);
        f.setVoucherSide(side);
        return f;
    }
}
