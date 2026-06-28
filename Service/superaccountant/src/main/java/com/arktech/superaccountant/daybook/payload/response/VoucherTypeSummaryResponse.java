package com.arktech.superaccountant.daybook.payload.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-voucher-type row DTO for the summary endpoint (D-09).
 * Columns: voucherTypeName, count, totalDebit, totalCredit, minDate, maxDate.
 * Mapped from ParsedVoucherRepository.findVoucherTypeSummary() Object[] rows.
 */
@Data
@Builder
public class VoucherTypeSummaryResponse {
    private String voucherTypeName;
    private Long count;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private LocalDate minDate;
    private LocalDate maxDate;
}
