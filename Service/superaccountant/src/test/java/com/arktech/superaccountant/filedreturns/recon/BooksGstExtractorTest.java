package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.GstTestFixtures;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BooksGstExtractorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 4, 10);
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO = LocalDate.of(2026, 4, 30);

    private final BooksGstExtractor extractor = new BooksGstExtractor();

    private Map<String, TaxLedgerMapping> mappings() {
        Map<String, TaxLedgerMapping> map = new HashMap<>();
        map.put("output cgst", mapping("Output CGST", TaxLedgerRole.OUTPUT, GstHead.CGST));
        map.put("output sgst", mapping("Output SGST", TaxLedgerRole.OUTPUT, GstHead.SGST));
        map.put("output igst", mapping("Output IGST", TaxLedgerRole.OUTPUT, GstHead.IGST));
        map.put("output cess", mapping("Output Cess", TaxLedgerRole.OUTPUT, GstHead.CESS));
        map.put("input cgst", mapping("Input CGST", TaxLedgerRole.INPUT, GstHead.CGST));
        map.put("input sgst", mapping("Input SGST", TaxLedgerRole.INPUT, GstHead.SGST));
        map.put("cgst rcm payable", mapping("CGST RCM Payable", TaxLedgerRole.RCM_LIABILITY, GstHead.CGST));
        map.put("input cgst rcm", mapping("Input CGST RCM", TaxLedgerRole.RCM_INPUT, GstHead.CGST));
        map.put("not tax cgst", mapping("Weird CGST", TaxLedgerRole.NOT_TAX, GstHead.CGST));
        return map;
    }

    private TaxLedgerMapping mapping(String ledgerName, TaxLedgerRole role, GstHead head) {
        TaxLedgerMapping m = GstTestFixtures.mapping(ledgerName, role, head);
        return m;
    }

    private HeadAmounts line(BooksGst books, String row) {
        return books.line(row);
    }

    @Test
    void salesInvoiceBecomesDocumentWithHeadsAndRates() {
        ParsedVoucher v = GstTestFixtures.voucher("Sales", DAY, "Acme Pvt Ltd", "27AAAPL1234C1ZV");
        GstTestFixtures.entryWithRate(v, "Sales 18%", "10000", "18", "Taxable");
        GstTestFixtures.entry(v, "Output CGST", "-900");
        GstTestFixtures.entry(v, "Output SGST", "-900");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(books.salesDocuments()).hasSize(1);
        BooksDocument doc = books.salesDocuments().get(0);
        assertThat(doc.kind()).isEqualTo(DocKind.INV);
        assertThat(doc.rcm()).isFalse();
        assertThat(doc.amounts().taxable()).isEqualByComparingTo("10000");
        assertThat(doc.amounts().cgst()).isEqualByComparingTo("900");
        assertThat(doc.amounts().sgst()).isEqualByComparingTo("900");
        assertThat(doc.amounts().igst()).isEqualByComparingTo("0");
        assertThat(doc.taxableByRate()).containsEntry(new BigDecimal("18"), new BigDecimal("10000"));
        assertThat(line(books, "3.1a").taxable()).isEqualByComparingTo("10000");
        assertThat(line(books, "3.1a").cgst()).isEqualByComparingTo("900");
    }

    @Test
    void creditNoteIsCnAndSubtractsFrom3_1a() {
        ParsedVoucher v = GstTestFixtures.voucher("Credit Note", DAY, "Acme Pvt Ltd", "27AAAPL1234C1ZV");
        GstTestFixtures.entryWithRate(v, "Sales 18%", "2000", "18", "Taxable");
        GstTestFixtures.entry(v, "Output CGST", "-180");
        GstTestFixtures.entry(v, "Output SGST", "-180");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(books.salesDocuments().get(0).kind()).isEqualTo(DocKind.CN);
        assertThat(line(books, "3.1a").taxable()).isEqualByComparingTo("-2000");
        assertThat(line(books, "3.1a").cgst()).isEqualByComparingTo("-180");
    }

    @Test
    void cancelledAndOutOfRangeVouchersExcluded() {
        ParsedVoucher cancelled = GstTestFixtures.voucher("Sales", DAY, "Acme", "27AAAPL1234C1ZV");
        cancelled.setCancelled(true);
        GstTestFixtures.entryWithRate(cancelled, "Sales 18%", "5000", "18", "Taxable");

        ParsedVoucher outOfRange = GstTestFixtures.voucher("Sales", LocalDate.of(2026, 5, 2), "Acme", "27AAAPL1234C1ZV");
        GstTestFixtures.entryWithRate(outOfRange, "Sales 18%", "5000", "18", "Taxable");

        BooksGst books = extractor.extract(List.of(cancelled, outOfRange), mappings(), FROM, TO);

        assertThat(books.salesDocuments()).isEmpty();
        assertThat(line(books, "3.1a")).isEqualTo(HeadAmounts.ZERO);
    }

    @Test
    void mappingNotNameDecidesTaxEntries() {
        ParsedVoucher v = GstTestFixtures.voucher("Sales", DAY, "Acme", "27AAAPL1234C1ZV");
        GstTestFixtures.entryWithRate(v, "Sales 18%", "10000", "18", "Taxable");
        GstTestFixtures.entryWithRate(v, "Weird CGST", "-900", "18", "Taxable");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(line(books, "3.1a").taxable()).isEqualByComparingTo("10900");
        assertThat(line(books, "3.1a").cgst()).isEqualByComparingTo("0");
        assertThat(books.salesDocuments().get(0).amounts().cgst()).isEqualByComparingTo("0");
    }

    @Test
    void rcmSplitsInto3_1dAnd4A() {
        ParsedVoucher v = GstTestFixtures.voucher("Purchase", DAY, "Freight Guy", "33AAAPL1234C1ZV");
        GstTestFixtures.entryWithRate(v, "Freight 5%", "1800", "5", "Taxable");
        GstTestFixtures.entry(v, "CGST RCM Payable", "-90");
        GstTestFixtures.entry(v, "Input CGST RCM", "90");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(line(books, "3.1d").taxable()).isEqualByComparingTo("1800");
        assertThat(line(books, "3.1d").cgst()).isEqualByComparingTo("90");
        assertThat(line(books, "4A").taxable()).isEqualByComparingTo("0");
        assertThat(line(books, "4A").cgst()).isEqualByComparingTo("90");
    }

    @Test
    void inputOnPurchaseGoesTo4A() {
        ParsedVoucher v = GstTestFixtures.voucher("Purchase", DAY, "Vendor", "24AAACD1111F1Z5");
        GstTestFixtures.entryWithRate(v, "Goods 18%", "5000", "18", "Taxable");
        GstTestFixtures.entry(v, "Input CGST", "450");
        GstTestFixtures.entry(v, "Input SGST", "450");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(line(books, "4A").cgst()).isEqualByComparingTo("450");
        assertThat(line(books, "4A").sgst()).isEqualByComparingTo("450");
        assertThat(line(books, "3.1a")).isEqualTo(HeadAmounts.ZERO);
    }

    @Test
    void debitNotePurchaseReturnSubtractsFrom4A() {
        ParsedVoucher purchase = GstTestFixtures.voucher("Purchase", DAY, "Vendor", "24AAACD1111F1Z5");
        GstTestFixtures.entryWithRate(purchase, "Goods 18%", "5000", "18", "Taxable");
        GstTestFixtures.entry(purchase, "Input CGST", "450");

        ParsedVoucher dn = GstTestFixtures.voucher("Debit Note", DAY, "Vendor", "24AAACD1111F1Z5");
        GstTestFixtures.entryWithRate(dn, "Goods 18%", "1000", "18", "Taxable");
        GstTestFixtures.entry(dn, "Input CGST", "90");

        BooksGst books = extractor.extract(List.of(purchase, dn), mappings(), FROM, TO);

        assertThat(line(books, "4A").cgst()).isEqualByComparingTo("360");
    }

    @Test
    void taxableOnlyCountsEntriesWithRateAndNonParty() {
        ParsedVoucher v = GstTestFixtures.voucher("Sales", DAY, "Acme", "27AAAPL1234C1ZV");
        GstTestFixtures.entry(v, "Freight", "500");
        GstTestFixtures.entryWithRate(v, "Acme Pvt Ltd", "-10000", "18", "Taxable").setPartyLedger(true);
        GstTestFixtures.entry(v, "Output IGST", "-1800");

        BooksGst books = extractor.extract(List.of(v), mappings(), FROM, TO);

        assertThat(line(books, "3.1a").taxable()).isEqualByComparingTo("0");
        assertThat(line(books, "3.1a").igst()).isEqualByComparingTo("1800");
        assertThat(books.salesDocuments().get(0).taxableByRate()).isEmpty();
    }
}
