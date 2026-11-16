package com.arktech.superaccountant.filedreturns;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "filed_return_lines")
@Data
@NoArgsConstructor
public class FiledReturnLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "table_code", nullable = false, length = 16)
    private String tableCode;

    @Column(name = "doc_type", length = 8)
    private String docType;

    @Column(name = "counterparty_gstin", length = 15)
    private String counterpartyGstin;

    @Column(name = "doc_number")
    private String docNumber;

    @Column(name = "doc_date")
    private LocalDate docDate;

    @Column(name = "original_doc_number")
    private String originalDocNumber;

    @Column(name = "original_doc_date")
    private LocalDate originalDocDate;

    @Column(name = "original_period", length = 7)
    private String originalPeriod;

    @Column(name = "supply_type", length = 8)
    private String supplyType;

    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;

    @Column(name = "rate", precision = 19, scale = 2)
    private BigDecimal rate;

    @Column(name = "invoice_type", length = 8)
    private String invoiceType;

    @Column(name = "label")
    private String label;

    @Column(name = "invoice_value", precision = 19, scale = 2)
    private BigDecimal invoiceValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxable = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal igst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cess = BigDecimal.ZERO;
}
