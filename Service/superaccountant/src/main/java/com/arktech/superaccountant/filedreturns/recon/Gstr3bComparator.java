package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.review.EvidenceHash;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Compares the filed GSTR-3B summary rows 3.1(a), 3.1(d) and 4A against the books. */
public final class Gstr3bComparator {

    private static final Set<String> COMPARED_ROWS = Set.of("3.1a", "3.1d", "4A");

    private Gstr3bComparator() {
    }

    public static List<FiledReturnDiscrepancy> compare(UUID organizationId, UUID workflowId,
                                                       FiledReturnSnapshot snapshot,
                                                       List<FiledReturnLine> lines,
                                                       BooksGst books) {
        List<FiledReturnDiscrepancy> out = new ArrayList<>();
        for (String row : COMPARED_ROWS) {
            HeadAmounts filed = filedRow(lines, row);
            HeadAmounts actual = books.line(row);
            compareRow(organizationId, workflowId, snapshot, row, filed, actual).ifPresent(out::add);
        }
        return out;
    }

    /**
     * The stable finding key: formType + "|" + tableCode + "|" + (document key or the row code
     * for summary rows) + "|" + counterparty GSTIN (empty when there is none).
     */
    public static String findingKey(FiledFormType formType, String tableCode, String documentKey,
                                    String counterpartyGstin) {
        return formType.name() + "|" + tableCode + "|" + (documentKey == null ? "" : documentKey)
                + "|" + (counterpartyGstin == null ? "" : counterpartyGstin);
    }

    public static String amountsString(HeadAmounts a) {
        return "T " + money(a.taxable()) + " | I " + money(a.igst()) + " | C " + money(a.cgst())
                + " | S " + money(a.sgst()) + " | Cess " + money(a.cess());
    }

    static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private static HeadAmounts filedRow(List<FiledReturnLine> lines, String row) {
        String prefix = "4A".equals(row) ? "4A" : null;
        HeadAmounts sum = HeadAmounts.ZERO;
        for (FiledReturnLine line : lines) {
            String code = line.getTableCode();
            boolean matches = row.equals(code) || (prefix != null && code != null && code.startsWith(prefix));
            if (!matches) {
                continue;
            }
            sum = sum.plus(new HeadAmounts(line.getTaxable(), line.getIgst(), line.getCgst(),
                    line.getSgst(), line.getCess()));
        }
        return sum;
    }

    private static Optional<FiledReturnDiscrepancy> compareRow(UUID organizationId, UUID workflowId,
                                                               FiledReturnSnapshot snapshot, String row,
                                                               HeadAmounts filed, HeadAmounts books) {
        if (!filed.taxDiffersFrom(books) && !filed.taxableDiffersFrom(books)) {
            return Optional.empty();
        }
        boolean material = filed.taxDiffersFrom(books);
        FiledReturnDiscrepancy d = new FiledReturnDiscrepancy();
        d.setOrganizationId(organizationId);
        d.setWorkflowId(workflowId);
        d.setSnapshotId(snapshot.getId());
        d.setFormType(FiledFormType.GSTR3B);
        d.setTableCode(row);
        d.setKind(DiscrepancyKind.VALUE_DIFFERENCE);
        d.setGrain(DiscrepancyGrain.SUMMARY);
        d.setMaterial(material);
        d.setSeverity(material ? FindingSeverity.WARNING : FindingSeverity.INFO);
        d.setFiledTaxable(filed.taxable());
        d.setFiledIgst(filed.igst());
        d.setFiledCgst(filed.cgst());
        d.setFiledSgst(filed.sgst());
        d.setFiledCess(filed.cess());
        d.setBooksTaxable(books.taxable());
        d.setBooksIgst(books.igst());
        d.setBooksCgst(books.cgst());
        d.setBooksSgst(books.sgst());
        d.setBooksCess(books.cess());
        d.setMessage(material ? taxMessage(row, filed, books) : taxableMessage(row, filed, books));
        d.setFindingKey(findingKey(FiledFormType.GSTR3B, row, row, null));
        d.setEvidenceHash(EvidenceHash.of("FILED_RETURN_" + d.getKind().name(), d.getFindingKey(),
                amountsString(filed), amountsString(books), snapshot.getContentHash()));
        return Optional.of(d);
    }

    private static String taxMessage(String row, HeadAmounts filed, HeadAmounts books) {
        String head;
        BigDecimal filedValue;
        BigDecimal booksValue;
        if (differs(filed.igst(), books.igst())) {
            head = "IGST";
            filedValue = filed.igst();
            booksValue = books.igst();
        } else if (differs(filed.cgst(), books.cgst())) {
            head = "CGST";
            filedValue = filed.cgst();
            booksValue = books.cgst();
        } else if (differs(filed.sgst(), books.sgst())) {
            head = "SGST";
            filedValue = filed.sgst();
            booksValue = books.sgst();
        } else {
            head = "Cess";
            filedValue = filed.cess();
            booksValue = books.cess();
        }
        return "GSTR-3B " + row + ": filed " + head + " " + money(filedValue) + " vs books " + head + " "
                + money(booksValue) + " (diff " + money(filedValue.subtract(booksValue).abs()) + ")";
    }

    private static String taxableMessage(String row, HeadAmounts filed, HeadAmounts books) {
        return "GSTR-3B " + row + ": filed taxable " + money(filed.taxable()) + " vs books taxable "
                + money(books.taxable()) + " (diff " + money(filed.taxable().subtract(books.taxable()).abs()) + ")";
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(GstRuleSupport.TOLERANCE) > 0;
    }
}
