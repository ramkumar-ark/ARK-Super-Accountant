package com.arktech.superaccountant.tds.payload.response;

import com.arktech.superaccountant.tds.models.TdsChallan;

import java.math.BigDecimal;
import java.util.List;

public record ChallanListResponse(List<TdsChallan> challans, BigDecimal tdsDeducted,
                                  BigDecimal approvedTdsTotal, BigDecimal excess) {
}
