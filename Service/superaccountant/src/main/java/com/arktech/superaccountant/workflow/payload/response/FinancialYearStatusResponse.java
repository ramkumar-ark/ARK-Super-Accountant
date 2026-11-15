package com.arktech.superaccountant.workflow.payload.response;

import java.time.LocalDate;
import java.util.List;

public record FinancialYearStatusResponse(
        int financialYear,
        LocalDate start,
        LocalDate end,
        boolean closed,
        String closeType,
        String itrAckNo,
        LocalDate itrAckDate,
        String note,
        boolean closable,
        List<String> openItems,
        List<String> blockers) {
}
