package com.arktech.superaccountant.gstr1.payload.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Gstr1TablesResponse(
        UUID workflowId,
        String stage,
        Instant builtAt,
        Instant frozenAt,
        List<TableSummary> tables,
        List<FillInSummary> fillIns) {

    public record TableSummary(
            String tableCode,
            long rowCount,
            Map<String, BigDecimal> totals) {
    }

    public record FillInSummary(
            String scope,
            String key,
            String attribute,
            String value,
            String enteredBy,
            Instant enteredAt,
            boolean fixedInPrepare) {
    }
}
