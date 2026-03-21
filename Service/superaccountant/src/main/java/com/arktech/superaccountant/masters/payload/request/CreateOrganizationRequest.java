package com.arktech.superaccountant.masters.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrganizationRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
}
