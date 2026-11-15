package com.arktech.superaccountant.tds.payload.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChallanRequest(String bsrCode, LocalDate depositDate, String challanSerialNumber,
                             String cin, BigDecimal tdsAmount, BigDecimal surcharge,
                             BigDecimal educationCess, BigDecimal interest, BigDecimal penalty,
                             boolean nilChallan) {
}
