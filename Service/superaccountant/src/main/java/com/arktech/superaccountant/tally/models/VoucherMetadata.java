package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoucherMetadata {
    private String type;
    private String remoteid;
    private String vchkey;
    private String vchtype;
    private String action;
    private String objview;
}
