package com.arktech.superaccountant.gstr1.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Gstr1StateNormalizationTest {

    @Test
    void normalizesStateNamesToCodes() {
        assertEquals("29", Gstr1Rules.normalizeStateCode("Karnataka"));
        assertEquals("19", Gstr1Rules.normalizeStateCode("West Bengal"));
        assertEquals("33", Gstr1Rules.normalizeStateCode("Tamil Nadu"));
        assertEquals("97", Gstr1Rules.normalizeStateCode("Other Territory"));
    }

    @Test
    void acceptsValidCodesAndCodePrefixedNames() {
        assertEquals("29", Gstr1Rules.normalizeStateCode("29"));
        assertEquals("07", Gstr1Rules.normalizeStateCode("07-Delhi"));
    }

    @Test
    void returnsNullWhenUnresolvable() {
        assertNull(Gstr1Rules.normalizeStateCode("Atlantis"));
        assertNull(Gstr1Rules.normalizeStateCode(""));
        assertNull(Gstr1Rules.normalizeStateCode(null));
    }
}
