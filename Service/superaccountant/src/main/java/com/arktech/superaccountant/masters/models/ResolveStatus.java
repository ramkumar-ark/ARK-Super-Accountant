package com.arktech.superaccountant.masters.models;

public enum ResolveStatus {
    OPEN,         // default; rejected findings return to this
    ACKNOWLEDGED, // operator provided reason, awaiting accountant/auditor review
    APPROVED,     // accountant/auditor approved — terminal
    RESOLVED,     // system auto-resolved when inline ledger edit satisfies the rule — terminal
    DISCARDED     // finding belongs to a discarded upload job
}
