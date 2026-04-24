package com.arktech.superaccountant.login.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ERoleTest {

    @Test
    void enumHasExactlyFourValues() {
        assertEquals(4, ERole.values().length,
                "ERole must have exactly 4 values: ROLE_OWNER, ROLE_ACCOUNTANT, ROLE_OPERATOR, ROLE_AUDITOR_CA");
    }

    @Test
    void targetValuesPresent() {
        assertDoesNotThrow(() -> ERole.valueOf("ROLE_OWNER"));
        assertDoesNotThrow(() -> ERole.valueOf("ROLE_ACCOUNTANT"));
        assertDoesNotThrow(() -> ERole.valueOf("ROLE_OPERATOR"));
        assertDoesNotThrow(() -> ERole.valueOf("ROLE_AUDITOR_CA"));
    }

    @Test
    void obsoleteValuesAbsent() {
        assertThrows(IllegalArgumentException.class, () -> ERole.valueOf("ROLE_CASHIER"),
                "ROLE_CASHIER must be removed from ERole");
        assertThrows(IllegalArgumentException.class, () -> ERole.valueOf("ROLE_DATA_ENTRY_OPERATOR"),
                "ROLE_DATA_ENTRY_OPERATOR must be removed from ERole");
    }
}
