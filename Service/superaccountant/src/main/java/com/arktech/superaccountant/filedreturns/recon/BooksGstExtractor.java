package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reduces the day book into everything the filed-return comparison needs: the sales-side
 * documents and the books' own GSTR-3B totals for 3.1(a), 3.1(d) and 4A, classified
 * through the saved tax-ledger mapping rather than ledger names.
 */
@Component
public class BooksGstExtractor {

    public BooksGst extract(List<ParsedVoucher> vouchers, Map<String, TaxLedgerMapping> mappingByKey,
                            LocalDate from, LocalDate to) {
        List<BooksDocument> salesDocuments = new ArrayList<>();
        HeadAmounts outbound = HeadAmounts.ZERO;
        HeadAmounts rcmLiability = HeadAmounts.ZERO;
        HeadAmounts input = HeadAmounts.ZERO;

        for (ParsedVoucher voucher : vouchers) {
            if (voucher.isCancelled() || voucher.getVoucherDate() == null
                    || voucher.getVoucherDate().isBefore(from) || voucher.getVoucherDate().isAfter(to)) {
                continue;
            }
            boolean salesSide = GstRuleSupport.isSalesSide(voucher.getVoucherTypeName());
            boolean purchaseSide = GstRuleSupport.isPurchaseSide(voucher.getVoucherTypeName());

            HeadAmounts taxable = HeadAmounts.ZERO;
            HeadAmounts output = HeadAmounts.ZERO;
            HeadAmounts rcmPayable = HeadAmounts.ZERO;
            HeadAmounts voucherInput = HeadAmounts.ZERO;
            HeadAmounts rcmInput = HeadAmounts.ZERO;
            Map<BigDecimal, BigDecimal> taxableByRate = new TreeMap<>();

            for (ParsedVoucherEntry entry : entries(voucher)) {
                TaxLedgerMapping mapping = taxEntry(entry, mappingByKey);
                BigDecimal amount = abs(entry);
                if (mapping == null) {
                    if (entry.getGstRate() != null && !entry.isPartyLedger()) {
                        taxable = taxable.plus(new HeadAmounts(amount, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                        taxableByRate.merge(entry.getGstRate(), amount, BigDecimal::add);
                    }
                    continue;
                }
                HeadAmounts head = headAmounts(mapping, amount);
                switch (mapping.getRole()) {
                    case OUTPUT -> output = output.plus(head);
                    case RCM_LIABILITY -> rcmPayable = rcmPayable.plus(head);
                    case INPUT -> voucherInput = voucherInput.plus(head);
                    case RCM_INPUT -> rcmInput = rcmInput.plus(head);
                    case CASH_BALANCE, LIABILITY_BALANCE, CREDIT_BALANCE, GST_TDS, NOT_TAX -> { }
                }
            }

            if (salesSide) {
                DocKind kind = docKind(voucher.getVoucherTypeName());
                BooksDocument document = new BooksDocument(
                        voucher.getId(),
                        voucher.getVoucherNumber(),
                        DocKeys.normalise(voucher.getVoucherNumber()),
                        DocKeys.loose(voucher.getVoucherNumber()),
                        voucher.getVoucherDate(),
                        voucher.getPartyGstin(),
                        kind,
                        voucher.isReverseChargeApplicable(),
                        new HeadAmounts(taxable.taxable(), output.igst(), output.cgst(),
                                output.sgst(), output.cess()),
                        Map.copyOf(taxableByRate));
                salesDocuments.add(document);
                if (!document.rcm()) {
                    outbound = document.kind() == DocKind.CN
                            ? outbound.minus(document.amounts())
                            : outbound.plus(document.amounts());
                }
            }

            if (rcmPayable.taxDiffersFrom(HeadAmounts.ZERO) || rcmPayable.taxableDiffersFrom(HeadAmounts.ZERO)) {
                rcmLiability = rcmLiability.plus(rcmPayable.plus(taxable));
            }
            if (purchaseSide) {
                HeadAmounts purchaseInput = voucherInput.plus(rcmInput);
                input = docKind(voucher.getVoucherTypeName()) == DocKind.DN
                        ? input.minus(purchaseInput)
                        : input.plus(purchaseInput);
            } else if (rcmInput.taxDiffersFrom(HeadAmounts.ZERO)) {
                input = input.plus(rcmInput);
            }
        }

        Map<String, HeadAmounts> lines = new LinkedHashMap<>();
        lines.put("3.1a", outbound);
        lines.put("3.1d", rcmLiability);
        lines.put("4A", input);
        return new BooksGst(salesDocuments, lines);
    }

    private TaxLedgerMapping taxEntry(ParsedVoucherEntry entry, Map<String, TaxLedgerMapping> mappingByKey) {
        if (entry.getLedgerName() == null) return null;
        return mappingByKey.get(entry.getLedgerName().trim().toLowerCase());
    }

    private HeadAmounts headAmounts(TaxLedgerMapping mapping, BigDecimal amount) {
        BigDecimal igst = mapping.getHead() == GstHead.IGST ? amount : BigDecimal.ZERO;
        BigDecimal cgst = mapping.getHead() == GstHead.CGST ? amount : BigDecimal.ZERO;
        BigDecimal sgst = mapping.getHead() == GstHead.SGST ? amount : BigDecimal.ZERO;
        BigDecimal cess = mapping.getHead() == GstHead.CESS ? amount : BigDecimal.ZERO;
        return new HeadAmounts(BigDecimal.ZERO, igst, cgst, sgst, cess);
    }

    private BigDecimal abs(ParsedVoucherEntry entry) {
        return entry.getAmount() == null ? BigDecimal.ZERO : entry.getAmount().abs();
    }

    private List<ParsedVoucherEntry> entries(ParsedVoucher voucher) {
        return voucher.getEntries() == null ? List.of() : voucher.getEntries();
    }

    private DocKind docKind(String voucherTypeName) {
        if ("Credit Note".equalsIgnoreCase(voucherTypeName)) return DocKind.CN;
        if ("Debit Note".equalsIgnoreCase(voucherTypeName)) return DocKind.DN;
        return DocKind.INV;
    }
}
