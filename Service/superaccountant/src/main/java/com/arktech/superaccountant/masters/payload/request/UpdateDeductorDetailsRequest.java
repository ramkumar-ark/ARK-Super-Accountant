package com.arktech.superaccountant.masters.payload.request;

import lombok.Data;

@Data
public class UpdateDeductorDetailsRequest {
    private String tan;
    private String deductorType;
    private String responsiblePersonName;
    private String responsiblePersonDesignation;
}
