package com.arktech.superaccountant.workflow.payload.response;

import java.util.List;

public record CloseFinancialYearResponse(
        FinancialYearStatusResponse year,
        List<String> warnings) {
}
