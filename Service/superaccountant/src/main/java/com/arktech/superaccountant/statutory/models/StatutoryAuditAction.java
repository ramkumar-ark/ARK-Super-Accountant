package com.arktech.superaccountant.statutory.models;

/** Kinds of app-administrator writes recorded in the statutory audit trail. */
public enum StatutoryAuditAction {
    /** A new dated rule row was added to a section. */
    RULE_ADDED,
    /** An open-ended rule row was end-dated because a newer row superseded it. */
    RULE_SUPERSEDED,
    SECTION_CREATED,
    SECTION_DEACTIVATED,
    SECTION_ACTIVATED,
    SECTION_DELETED
}
