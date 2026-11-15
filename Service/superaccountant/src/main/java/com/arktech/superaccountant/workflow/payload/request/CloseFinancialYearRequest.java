package com.arktech.superaccountant.workflow.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class CloseFinancialYearRequest {
    private String closeType;

    private String itrAckNo;

    private LocalDate itrAckDate;

    private String note;
}
