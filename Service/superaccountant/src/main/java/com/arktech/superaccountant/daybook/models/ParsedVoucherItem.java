package com.arktech.superaccountant.daybook.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item line of a captured voucher (Tally INVENTORYENTRIES.LIST), stored
 * outside the content hash like the GST section so later item-level edits do
 * not invalidate tamper evidence. Null when Tally did not send the field.
 */
@Entity
@Table(name = "parsed_voucher_items")
@Data
@NoArgsConstructor
public class ParsedVoucherItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parsed_voucher_id", nullable = false)
    private ParsedVoucher parsedVoucher;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "stock_item_name")
    private String stockItemName;

    @Column(name = "hsn")
    private String hsn;

    // GST UQC after mapping (UqcMapper); raw Tally unit preserved in unit
    @Column(name = "uqc", length = 3)
    private String uqc;

    // Raw Tally unit symbol, stored unnormalised
    @Column(name = "unit")
    private String unit;

    // Numeric quantity parsed from Tally ACTUALQTY/BILLEDQTY ("10 NOS" → 10)
    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "rate", precision = 19, scale = 4)
    private BigDecimal rate;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
}
