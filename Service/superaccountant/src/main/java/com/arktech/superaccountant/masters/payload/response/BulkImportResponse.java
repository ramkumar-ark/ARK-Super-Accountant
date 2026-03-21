package com.arktech.superaccountant.masters.payload.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkImportResponse {
    private int created;
    private String message;
}
