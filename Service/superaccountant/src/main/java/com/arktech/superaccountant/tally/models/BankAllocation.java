package com.arktech.superaccountant.tally.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankAllocation {
    private String date;
    private String instrumentdate;
    private String transactiontype;
    private String paymentfavouring;
    private String bankpartyname;
    private String bankmanualstatus;
    private String paymentmode;
    private String amount;
    private String chequerange;
    private String uniquereferencenumber;
}
