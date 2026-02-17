package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GstRegistration {
    private String value;
    private String taxtype;
    private String taxregistration;
}
