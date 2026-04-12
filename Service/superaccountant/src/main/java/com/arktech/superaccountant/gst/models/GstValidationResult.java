package com.arktech.superaccountant.gst.models;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GstValidationResult {
    private String voucherNumber;
    private String voucherType;
    private String date;
    private String partyName;
    private List<GstValidationError> errors = new ArrayList<>();

    public GstValidationResult(String voucherNumber, String voucherType, String date, String partyName) {
        this.voucherNumber = voucherNumber;
        this.voucherType = voucherType;
        this.date = date;
        this.partyName = partyName;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
