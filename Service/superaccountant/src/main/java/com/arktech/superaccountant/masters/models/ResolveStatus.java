package com.arktech.superaccountant.masters.models;

public enum ResolveStatus {
    OPEN,         // default; rejected findings return to this
    ACKNOWLEDGED, // proposal submitted by an operator, awaiting accountant approval (still blocks the gate)
    APPROVED,     // accountant/auditor approved — terminal
    RESOLVED,     // system auto-resolved when inline ledger edit satisfies the rule — terminal
    DISCARDED     // finding belongs to a discarded upload job
}
