package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveRequest {
    @NotNull
    private ResolveStatus status;
    private String note;
}
