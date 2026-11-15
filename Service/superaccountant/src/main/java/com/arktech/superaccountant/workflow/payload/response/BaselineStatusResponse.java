package com.arktech.superaccountant.workflow.payload.response;

import java.time.LocalDate;
import java.util.List;

public record BaselineStatusResponse(
        boolean confirmed,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        List<TypeStatus> types,
        List<String> blockers,
        String importedFiledData,
        Integer discrepancyCount) {

    public record TypeStatus(String type, int unconfirmedRows, int confirmedRows, LocalDate latestEnd) {
    }
}
