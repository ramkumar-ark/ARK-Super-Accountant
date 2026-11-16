package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.recon.NetFiledPosition.B2csKey;
import com.arktech.superaccountant.gst.rules.GstRuleSupport;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.review.EvidenceHash;

import java.math.BigDecimal;import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Gstr1Reconciler {

    private static final BigDecimal B2CL_THRESHOLD_CURRENT = new BigDecimal("100000");
    private static final BigDecimal B2CL_THRESHOLD_BEFORE_AUG_2024 = new BigDecimal("250000");
    private static final LocalDate B2CL_THRESHOLD_DATE = LocalDate.of(2024, 8, 1);

    private Gstr1Reconciler() {
    }

    public static List<FiledReturnDiscrepancy> reconcile(UUID organizationId, UUID workflowId,
                                                         NetFiledPosition filed, BooksGst books,
                                                         LocalDate coverStart, LocalDate coverEnd) {
        List<FiledReturnDiscrepancy> findings = new ArrayList<>();
        List<NetFiledPosition.FiledDocument> filedDocs = new ArrayList<>(filed.documents());
        List<BooksDocument> booksDocs = new ArrayList<>(books.salesDocuments());
        Map<B2csKey, HeadAmounts> booksB2cs = new HashMap<>();
        List<BooksDocument> b2clCandidates = new ArrayList<>();

        for (BooksDocument doc : booksDocs) {
            if (doc.partyGstin() != null && !doc.partyGstin().isBlank()) {
                continue;
            }
            BigDecimal invoiceValue = invoiceValue(doc);
            boolean intra = isIntra(doc);
            BigDecimal threshold = doc.date().isBefore(B2CL_THRESHOLD_DATE)
                    ? B2CL_THRESHOLD_BEFORE_AUG_2024 : B2CL_THRESHOLD_CURRENT;
            if (intra || invoiceValue.compareTo(threshold) <= 0) {
                addToBooksB2cs(booksB2cs, doc);
            } else {
                b2clCandidates.add(doc);
            }
        }

        List<BooksDocument> gstinBooks = booksDocs.stream()
                .filter(d -> d.partyGstin() != null && !d.partyGstin().isBlank()).toList();
        Set<UUID> matchedBooks = new HashSet<>();

        // 1. exact
        matchBy(organizationId, workflowId, filed, gstinBooks, new ArrayList<>(booksDocs), filedDocs, findings,
                (b, f) -> sameGstin(b, f) && b.docKey().equals(f.docKey()), false, coverStart, coverEnd, matchedBooks);
        // 2. loose
        matchBy(organizationId, workflowId, filed, gstinBooks, new ArrayList<>(booksDocs), filedDocs, findings,
                (b, f) -> sameGstin(b, f) && b.looseKey().equals(f.looseKey()), true, coverStart, coverEnd, matchedBooks);
        // 3. docKey only with GSTIN difference
        matchBy(organizationId, workflowId, filed, gstinBooks, new ArrayList<>(booksDocs), filedDocs, findings,
                (b, f) -> b.docKey().equals(f.docKey())
                        && f.counterpartyGstin() != null && !f.counterpartyGstin().isBlank()
                        && !sameGstin(b, f), false, coverStart, coverEnd, matchedBooks);

        // 6b. large inter-state unregistered books documents against filed B2CL
        for (BooksDocument b : b2clCandidates) {
            NetFiledPosition.FiledDocument match = null;
            for (NetFiledPosition.FiledDocument f : filedDocs) {
                if ("B2CL".equals(f.tableCode()) && b.docKey().equals(f.docKey())) {
                    match = f;
                    break;
                }
            }
            if (match == null) {
                findings.add(findings(organizationId, workflowId, DiscrepancyKind.BOOKS_ONLY, null, b,
                        "B2CL", false, coverStart, coverEnd));
            } else {
                filedDocs.remove(match);
                findings.addAll(comparePair(organizationId, workflowId, match, b, "B2CL", false));
            }
        }

        // 5. unmatched filed documents
        for (NetFiledPosition.FiledDocument f : filedDocs) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.PORTAL_ONLY, f, null,
                    f.tableCode(), false, coverStart, coverEnd));
        }

        // 6. unmatched books documents with GSTIN
        for (BooksDocument b : booksDocs) {
            if (b.partyGstin() == null || b.partyGstin().isBlank() || matchedBooks.contains(b.voucherId())) {
                continue;
            }
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.BOOKS_ONLY, null, b,
                    tableFor(b), false, coverStart, coverEnd));
        }

        // 7. B2CS totals
        findings.addAll(compareB2cs(organizationId, workflowId, filed, booksB2cs));
        return findings;
    }

    private interface Matcher {
        boolean matches(BooksDocument books, NetFiledPosition.FiledDocument filed);
    }

    private static void matchBy(UUID organizationId, UUID workflowId, NetFiledPosition filed,
                                List<BooksDocument> booksDocs, List<BooksDocument> unmatchedBooks,
                                List<NetFiledPosition.FiledDocument> filedDocs,
                                List<FiledReturnDiscrepancy> findings, Matcher matcher, boolean fuzzy,
                                LocalDate coverStart, LocalDate coverEnd, Set<UUID> matchedBooks) {
        for (BooksDocument b : booksDocs) {
            NetFiledPosition.FiledDocument match = null;
            for (NetFiledPosition.FiledDocument f : filedDocs) {
                if (matcher.matches(b, f)) {
                    match = f;
                    break;
                }
            }
            if (match != null) {
                filedDocs.remove(match);
                unmatchedBooks.remove(b);
                matchedBooks.add(b.voucherId());
                findings.addAll(comparePair(organizationId, workflowId, match, b, match.tableCode(), fuzzy));
            }
        }
    }


    private static List<FiledReturnDiscrepancy> comparePair(UUID organizationId, UUID workflowId,
                                                            NetFiledPosition.FiledDocument filed,
                                                            BooksDocument books, String tableCode,
                                                            boolean fuzzy) {
        List<FiledReturnDiscrepancy> findings = new ArrayList<>();
        boolean gstinDiffers = filed.counterpartyGstin() != null && !filed.counterpartyGstin().isBlank()
                && !filed.counterpartyGstin().equalsIgnoreCase(books.partyGstin());
        if (gstinDiffers) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.GSTIN_DIFFERENCE, filed, books,
                    tableCode, false, null, null));
        }
        if (filed.amounts().taxDiffersFrom(books.amounts())) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.VALUE_DIFFERENCE, filed, books,
                    tableCode, fuzzy, null, null));
        } else if (filed.amounts().taxableDiffersFrom(books.amounts())) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.VALUE_DIFFERENCE, filed, books,
                    tableCode, fuzzy, null, null));
        }
        if (filed.docDate() != null && books.date() != null && !filed.docDate().equals(books.date())) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.DATE_SHIFT, filed, books,
                    tableCode, fuzzy, null, null));
        }
        if (!filed.amounts().taxDiffersFrom(books.amounts())
                && !filed.taxableByRate().keySet().equals(books.taxableByRate().keySet())
                && !filed.amounts().taxableDiffersFrom(books.amounts())) {
            findings.add(findings(organizationId, workflowId, DiscrepancyKind.RATE_SPLIT, filed, books,
                    tableCode, fuzzy, null, null));
        }
        return findings;
    }

    private static List<FiledReturnDiscrepancy> compareB2cs(UUID organizationId, UUID workflowId,
                                                            NetFiledPosition filed,
                                                            Map<B2csKey, HeadAmounts> booksB2cs) {
        List<FiledReturnDiscrepancy> findings = new ArrayList<>();
        Set<B2csKey> keys = new HashSet<>(filed.b2csTotals().keySet());
        keys.addAll(booksB2cs.keySet());
        for (B2csKey key : keys) {
            HeadAmounts f = filed.b2csTotals().getOrDefault(key, HeadAmounts.ZERO);
            HeadAmounts b = booksB2cs.getOrDefault(key, HeadAmounts.ZERO);
            if (!f.taxDiffersFrom(b) && !f.taxableDiffersFrom(b)) {
                continue;
            }
            boolean material = f.taxDiffersFrom(b);
            String documentKey = rateLabel(key.rate()) + "-" + key.supplyType();
            FiledReturnDiscrepancy d = new FiledReturnDiscrepancy();
            d.setOrganizationId(organizationId);
            d.setWorkflowId(workflowId);
            d.setFormType(FiledFormType.GSTR1);
            d.setTableCode("B2CS");
            d.setKind(DiscrepancyKind.VALUE_DIFFERENCE);
            d.setGrain(DiscrepancyGrain.SUMMARY);
            d.setMaterial(material);
            d.setSeverity(material ? FindingSeverity.WARNING : FindingSeverity.INFO);
            d.setFiledTaxable(f.taxable());
            d.setFiledIgst(f.igst());
            d.setFiledCgst(f.cgst());
            d.setFiledSgst(f.sgst());
            d.setFiledCess(f.cess());
            d.setBooksTaxable(b.taxable());
            d.setBooksIgst(b.igst());
            d.setBooksCgst(b.cgst());
            d.setBooksSgst(b.sgst());
            d.setBooksCess(b.cess());
            d.setMessage(message("B2CS " + documentKey, f, b));
            d.setFindingKey(Gstr3bComparator.findingKey(FiledFormType.GSTR1, "B2CS", documentKey, null));
            d.setEvidenceHash(EvidenceHash.of("FILED_RETURN_" + d.getKind().name(), d.getFindingKey(),
                    Gstr3bComparator.amountsString(f), Gstr3bComparator.amountsString(b), ""));
            findings.add(d);
        }
        return findings;
    }

    private static FiledReturnDiscrepancy findings(UUID organizationId, UUID workflowId, DiscrepancyKind kind,
                                                   NetFiledPosition.FiledDocument filed, BooksDocument books,
                                                   String tableCode, boolean fuzzy,
                                                   LocalDate coverStart, LocalDate coverEnd) {
        FiledReturnDiscrepancy d = new FiledReturnDiscrepancy();
        d.setOrganizationId(organizationId);
        d.setWorkflowId(workflowId);
        boolean material = kind != DiscrepancyKind.DATE_SHIFT && kind != DiscrepancyKind.RATE_SPLIT;
        if (filed != null) {
            d.setSnapshotId(filed.snapshotId());
            d.setFormType(FiledFormType.GSTR1);
            d.setTableCode(tableCode);
            d.setKind(kind);
            d.setGrain(DiscrepancyGrain.DOCUMENT);
            d.setMaterial(material);
            d.setFuzzyMatch(fuzzy);
            d.setDocNumber(filed.docNumber());
            d.setDocDate(filed.docDate());
            d.setCounterpartyGstin(filed.counterpartyGstin());
            d.setAmendedInPeriod(filed.amendedInPeriod());
            d.setFiledTaxable(filed.amounts().taxable());
            d.setFiledIgst(filed.amounts().igst());
            d.setFiledCgst(filed.amounts().cgst());
            d.setFiledSgst(filed.amounts().sgst());
            d.setFiledCess(filed.amounts().cess());
        }
        if (books != null) {
            d.setFormType(FiledFormType.GSTR1);
            d.setTableCode(tableCode);
            d.setKind(kind);
            d.setGrain(DiscrepancyGrain.DOCUMENT);
            d.setMaterial(material);
            d.setFuzzyMatch(d.getFuzzyMatch() != null && d.getFuzzyMatch() || fuzzy);
            d.setDocNumber(d.getDocNumber() != null ? d.getDocNumber() : books.voucherNumber());
            d.setDocDate(d.getDocDate() != null ? d.getDocDate() : books.date());
            d.setCounterpartyGstin(d.getCounterpartyGstin() != null ? d.getCounterpartyGstin() : books.partyGstin());
            d.setVoucherId(books.voucherId());
            d.setVoucherNumber(books.voucherNumber());
            d.setBooksDate(books.date());
            d.setBooksGstin(books.partyGstin());
            d.setBooksTaxable(books.amounts().taxable());
            d.setBooksIgst(books.amounts().igst());
            d.setBooksCgst(books.amounts().cgst());
            d.setBooksSgst(books.amounts().sgst());
            d.setBooksCess(books.amounts().cess());
        }
        HeadAmounts f = filed == null ? HeadAmounts.ZERO
                : new HeadAmounts(d.getFiledTaxable(), d.getFiledIgst(), d.getFiledCgst(), d.getFiledSgst(), d.getFiledCess());
        HeadAmounts b = books == null ? HeadAmounts.ZERO
                : new HeadAmounts(d.getBooksTaxable(), d.getBooksIgst(), d.getBooksCgst(), d.getBooksSgst(), d.getBooksCess());
        String documentKey = filed != null ? filed.docKey() : DocKeys.normalise(books.voucherNumber());
        d.setMessage(message(tableCode + " " + (d.getDocNumber() == null ? documentKey : d.getDocNumber()), f, b));
        d.setFindingKey(Gstr3bComparator.findingKey(FiledFormType.GSTR1, tableCode, documentKey,
                filed != null ? filed.counterpartyGstin() : books.partyGstin()));
        String hash = filed != null ? "" : "";
        d.setEvidenceHash(EvidenceHash.of("FILED_RETURN_" + d.getKind().name(), d.getFindingKey(),
                Gstr3bComparator.amountsString(f), Gstr3bComparator.amountsString(b), hash));
        d.setSeverity(material ? FindingSeverity.WARNING : FindingSeverity.INFO);
        return d;
    }

    private static String message(String subject, HeadAmounts filed, HeadAmounts books) {
        if (differs(filed.igst(), books.igst())) {
            return headMessage(subject, "IGST", filed.igst(), books.igst());
        }
        if (differs(filed.cgst(), books.cgst())) {
            return headMessage(subject, "CGST", filed.cgst(), books.cgst());
        }
        if (differs(filed.sgst(), books.sgst())) {
            return headMessage(subject, "SGST", filed.sgst(), books.sgst());
        }
        if (differs(filed.cess(), books.cess())) {
            return headMessage(subject, "Cess", filed.cess(), books.cess());
        }
        return subject + ": filed taxable " + Gstr3bComparator.money(filed.taxable())
                + " vs books taxable " + Gstr3bComparator.money(books.taxable())
                + " (diff " + Gstr3bComparator.money(filed.taxable().subtract(books.taxable()).abs()) + ")";
    }

    private static String headMessage(String subject, String head, BigDecimal filedValue, BigDecimal booksValue) {
        return subject + ": filed " + head + " " + Gstr3bComparator.money(filedValue) + " vs books " + head
                + " " + Gstr3bComparator.money(booksValue)
                + " (diff " + Gstr3bComparator.money(filedValue.subtract(booksValue).abs()) + ")";
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(GstRuleSupport.TOLERANCE) > 0;
    }

    private static boolean sameGstin(BooksDocument b, NetFiledPosition.FiledDocument f) {
        return f.counterpartyGstin() != null && !f.counterpartyGstin().isBlank()
                && f.counterpartyGstin().equalsIgnoreCase(b.partyGstin());
    }

    private static boolean isIntra(BooksDocument doc) {
        HeadAmounts a = doc.amounts();
        return a.igst().signum() == 0;
    }

    private static BigDecimal invoiceValue(BooksDocument doc) {
        HeadAmounts a = doc.amounts();
        return a.taxable().add(a.igst()).add(a.cgst()).add(a.sgst()).add(a.cess());
    }

    private static void addToBooksB2cs(Map<B2csKey, HeadAmounts> totals, BooksDocument doc) {
        String supplyType = isIntra(doc) ? "INTRA" : "INTER";
        for (Map.Entry<BigDecimal, BigDecimal> e : doc.taxableByRate().entrySet()) {
            BigDecimal rate = e.getKey();
            BigDecimal rateTaxable = e.getValue();
            HeadAmounts total = doc.amounts();
            BigDecimal ratio = total.taxable().signum() == 0
                    ? BigDecimal.ONE : rateTaxable.divide(total.taxable(), 6, java.math.RoundingMode.HALF_UP);
            HeadAmounts share = new HeadAmounts(rateTaxable,
                    total.igst().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP),
                    total.cgst().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP),
                    total.sgst().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP),
                    total.cess().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP));
            totals.merge(new B2csKey(rate, supplyType), share, HeadAmounts::plus);
        }
        if (doc.taxableByRate().isEmpty()) {
            totals.merge(new B2csKey(BigDecimal.ZERO, supplyType), doc.amounts(), HeadAmounts::plus);
        }
    }

    private static String tableFor(BooksDocument doc) {
        return doc.kind() == DocKind.INV ? "B2B" : "CDNR";
    }

    private static String rateLabel(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}
