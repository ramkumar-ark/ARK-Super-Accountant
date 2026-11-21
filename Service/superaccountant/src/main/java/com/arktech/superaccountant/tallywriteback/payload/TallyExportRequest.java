package com.arktech.superaccountant.tallywriteback.payload;

import java.util.List;
import java.util.UUID;

public record TallyExportRequest(
        List<UUID> itemIds,
        Boolean confirmStale) {
}
