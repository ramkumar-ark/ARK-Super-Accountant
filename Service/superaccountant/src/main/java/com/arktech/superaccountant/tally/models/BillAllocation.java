package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillAllocation {
    private String name;
    private String billtype;
    private String amount;
    private boolean tdsdeducteeisspecialrate;
}
