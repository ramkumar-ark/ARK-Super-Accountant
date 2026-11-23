package com.arktech.superaccountant.tdsapplicability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityClassTest {

    @ParameterizedTest
    @CsvSource({"AAACA1234A,K", "AAAFA1234A,F", "AAAAA1234A,P", "AAATA1234A,T", "AAABA1234A,B",
            "AAAJA1234A,J", "AAAPA1234A,Q", "AAAHA1234A,Q"})
    void derivesDeductorCodeFromPan(String pan, String code) {
        assertEquals(Optional.of(code), EntityClass.deductorCodeFromPan(pan));
    }

    @Test
    void unknownOrBlankPanGivesEmpty() {
        assertTrue(EntityClass.deductorCodeFromPan("AAAGA1234A").isEmpty());
        assertTrue(EntityClass.deductorCodeFromPan(null).isEmpty());
        assertTrue(EntityClass.deductorCodeFromPan("bad").isEmpty());
    }

    @Test
    void deductorTypeWinsOverPan() {
        EntityClass.EffectiveCode effective = EntityClass.effectiveCode("AAAPA1234A", "K");
        assertEquals("K", effective.code());
        assertEquals("DEDUCTOR_TYPE", effective.source());
    }
}
