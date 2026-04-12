package com.arktech.superaccountant.masters.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkImportRequest {
    @NotNull
    @Size(max = 500, message = "Bulk import limited to 500 records per request.")
    @Valid
    private List<CreatePreconfiguredMasterRequest> masters;
}
