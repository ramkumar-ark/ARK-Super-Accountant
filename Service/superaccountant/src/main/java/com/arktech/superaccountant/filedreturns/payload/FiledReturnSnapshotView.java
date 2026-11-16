package com.arktech.superaccountant.filedreturns.payload;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FiledReturnSnapshotView(
        UUID id,
        UUID organizationId,
        String formType,
        String filingPeriod,
        LocalDate coverStart,
        LocalDate coverEnd,
        UUID baselineWorkflowId,
        String gstin,
        String sourceKind,
        UUID fileId,
        String parserVersion,
        String importedBy,
        Instant importedAt,
        Instant supersededAt,
        List<Line> lines) {

    public record Line(
            String tableCode,
            String docType,
            String counterpartyGstin,
            String docNumber,
            LocalDate docDate,
            String originalDocNumber,
            LocalDate originalDocDate,
            String originalPeriod,
            String supplyType,
            String placeOfSupply,
            java.math.BigDecimal rate,
            String invoiceType,
            String label,
            java.math.BigDecimal invoiceValue,
            java.math.BigDecimal taxable,
            java.math.BigDecimal igst,
            java.math.BigDecimal cgst,
            java.math.BigDecimal sgst,
            java.math.BigDecimal cess) {

        public static Line from(FiledReturnLine l) {
            return new Line(l.getTableCode(), l.getDocType(), l.getCounterpartyGstin(), l.getDocNumber(),
                    l.getDocDate(), l.getOriginalDocNumber(), l.getOriginalDocDate(), l.getOriginalPeriod(),
                    l.getSupplyType(), l.getPlaceOfSupply(), l.getRate(), l.getInvoiceType(), l.getLabel(),
                    l.getInvoiceValue(), l.getTaxable(), l.getIgst(), l.getCgst(), l.getSgst(), l.getCess());
        }
    }

    public static FiledReturnSnapshotView from(FiledReturnSnapshot s, List<FiledReturnLine> lines) {
        return new FiledReturnSnapshotView(
                s.getId(), s.getOrganizationId(), s.getFormType().name(), s.getFilingPeriod(),
                s.getCoverStart(), s.getCoverEnd(), s.getBaselineWorkflowId(), s.getGstin(),
                s.getSourceKind().name(), s.getFileId(), s.getParserVersion(),
                s.getImportedBy(), s.getImportedAt(), s.getSupersededAt(),
                lines.stream().map(Line::from).toList());
    }
}
