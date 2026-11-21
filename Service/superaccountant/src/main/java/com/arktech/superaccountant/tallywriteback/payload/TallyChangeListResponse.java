package com.arktech.superaccountant.tallywriteback.payload;

import java.util.List;

public record TallyChangeListResponse(
        MastersUpload mastersUpload,
        List<TallyChangeItemResponse> items) {
}
