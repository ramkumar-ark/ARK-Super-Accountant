package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherScopeClassifierTest {

    private final VoucherScopeClassifier classifier = new VoucherScopeClassifier();

    private UploadLedgerSnapshot ledger(String name, LedgerCategory category) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setCategory(category);
        return s;
    }

    private ParsedVoucher voucher(String voucherTypeName, ParsedVoucherEntry... entries) {
        ParsedVoucher v = new ParsedVoucher();
        v.setVoucherTypeName(voucherTypeName);
        v.setVoucherDate(LocalDate.of(2025, 4, 15));
        for (ParsedVoucherEntry e : entries) {
            e.setParsedVoucher(v);
        }
        v.setEntries(List.of(entries));
        return v;
    }

    private ParsedVoucherEntry entry(String ledgerName) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setLedgerName(ledgerName);
        e.setAmount(new BigDecimal("100.00"));
        e.setDebit(true);
        return e;
    }

    @Test
    void salesVoucher_gstr1AndGstr3b() {
        Map<String, UploadLedgerSnapshot> ledgers = Map.of(
                "Sales Account", ledger("Sales Account", LedgerCategory.INCOME));
        ParsedVoucher v = voucher("Sales", entry("Sales Account"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers))
                .isEqualTo(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF));
    }

    @Test
    void purchaseVoucher_gstr3bOnly() {
        Map<String, UploadLedgerSnapshot> ledgers = Map.of(
                "Cement Purchases", ledger("Cement Purchases", LedgerCategory.PURCHASE));
        ParsedVoucher v = voucher("Purchase", entry("Cement Purchases"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers))
                .isEqualTo(Set.of(WorkflowType.GSTR3B));
    }

    @Test
    void journalOnOutputGstLedger_gstr1AndGstr3b() {
        Map<String, UploadLedgerSnapshot> ledgers = Map.of(
                "Output CGST @9%", ledger("Output CGST @9%", LedgerCategory.GST));
        ParsedVoucher v = voucher("Journal", entry("Output CGST @9%"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers))
                .isEqualTo(Set.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF));
    }

    @Test
    void paymentTouchingTdsLedger_tdsTypes() {
        Map<String, UploadLedgerSnapshot> ledgers = Map.of(
                "TDS Payable", ledger("TDS Payable", LedgerCategory.TDS));
        ParsedVoucher v = voucher("Payment", entry("TDS Payable"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers))
                .isEqualTo(Set.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q));
    }

    @Test
    void expenseWithTdsSection_tdsTypes() {
        UploadLedgerSnapshot contractor = ledger("Contractor Fees", LedgerCategory.EXPENSE);
        contractor.setTdsSection("194C");
        Map<String, UploadLedgerSnapshot> ledgers = Map.of("Contractor Fees", contractor);
        ParsedVoucher v = voucher("Journal", entry("Contractor Fees"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers))
                .isEqualTo(Set.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q));
    }

    @Test
    void receiptWithNoGstOrTds_empty() {
        Map<String, UploadLedgerSnapshot> ledgers = Map.of(
                "Client ABC", ledger("Client ABC", LedgerCategory.OTHER));
        ParsedVoucher v = voucher("Receipt", entry("Client ABC"));
        assertThat(classifier.scopes(VoucherPayload.of(v), ledgers)).isEmpty();
    }

    @Test
    void unknownLedger_ignored() {
        ParsedVoucher v = voucher("Journal", entry("Mystery Ledger"));
        assertThat(classifier.scopes(VoucherPayload.of(v), Map.of())).isEmpty();
    }
}
