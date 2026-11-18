package com.arktech.superaccountant.tds.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RetroFlag(String voucherKey, LocalDate voucherDate, String section,
                        String deducteeLedger, BigDecimal shortfall) {
}
