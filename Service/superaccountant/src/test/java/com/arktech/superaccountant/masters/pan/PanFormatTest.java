package com.arktech.superaccountant.masters.pan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanFormatTest {

    @Test
    void acceptsTenCharacterPanWithSurroundingWhitespace() {
        assertThat(PanFormat.isValid(" AAPPK2262D ")).isTrue();
    }

    @Test
    void rejectsOverLengthPan() {
        assertThat(PanFormat.isValid("AAPPK2262D1")).isFalse();
    }

    @Test
    void rejectsLowercasePan() {
        assertThat(PanFormat.isValid("aappk2262d")).isFalse();
    }

    @Test
    void rejectsBlankAndNull() {
        assertThat(PanFormat.isValid(null)).isFalse();
        assertThat(PanFormat.isValid("   ")).isFalse();
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertThat(PanFormat.normalize(" aappk2262d ")).isEqualTo("AAPPK2262D");
        assertThat(PanFormat.normalize(null)).isNull();
    }
}
