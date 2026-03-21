package com.arktech.superaccountant.masters.payload.response;

import com.arktech.superaccountant.masters.models.*;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FindingResponse {
    private UUID id;
    private String ruleCode;
    private LedgerCategory category;
    private MismatchType mismatchType;
    private String ledgerName;
    private String expectedValue;
    private String actualValue;
    private FindingSeverity severity;
    private String message;
    private String suggestedFix;
    private ResolveStatus resolveStatus;
    private String resolveNote;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
