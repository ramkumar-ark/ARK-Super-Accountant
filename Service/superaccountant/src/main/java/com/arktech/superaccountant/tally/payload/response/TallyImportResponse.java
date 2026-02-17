package com.arktech.superaccountant.tally.payload.response;

import java.util.List;
import java.util.Map;

import com.arktech.superaccountant.tally.models.Voucher;
import lombok.Data;

@Data
public class TallyImportResponse {
    private int totalVouchers;
    private Map<String, Integer> voucherTypeCounts;
    private Map<String, List<Voucher>> vouchersByType;

    public TallyImportResponse(int totalVouchers, Map<String, Integer> voucherTypeCounts,
                               Map<String, List<Voucher>> vouchersByType) {
        this.totalVouchers = totalVouchers;
        this.voucherTypeCounts = voucherTypeCounts;
        this.vouchersByType = vouchersByType;
    }
}
