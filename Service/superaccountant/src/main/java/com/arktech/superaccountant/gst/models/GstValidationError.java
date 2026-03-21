package com.arktech.superaccountant.gst.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GstValidationError {
    private GstErrorType errorType;
    private String message;
    private String ledgerName;
    private String expectedValue;
    private String actualValue;

    public GstValidationError(GstErrorType errorType, String message) {
        this.errorType = errorType;
        this.message = message;
    }
}
