package com.arktech.superaccountant.filedreturns.recon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** One sales-side voucher reduced to the fields invoice-level matching needs. */
public record BooksDocument(
        UUID voucherId,
        String voucherNumber,
        String docKey,
        String looseKey,
        LocalDate date,
        String partyGstin,
        DocKind kind,
        boolean rcm,
        HeadAmounts amounts,
        Map<BigDecimal, BigDecimal> taxableByRate) {
}
