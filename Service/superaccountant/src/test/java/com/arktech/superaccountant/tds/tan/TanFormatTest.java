package com.arktech.superaccountant.tds.tan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TanFormatTest {

    @Test
    void normalizesByTrimmingAndUppercasing() {
        assertThat(TanFormat.normalize("  blra12345c  ")).isEqualTo("BLRA12345C");
    }

    @Test
    void treatsBlankAsNone() {
        assertThat(TanFormat.normalize(null)).isNull();
        assertThat(TanFormat.normalize("   ")).isNull();
    }

    @Test
    void acceptsValidTanAfterNormalization() {
        assertThat(TanFormat.isValid(" blra12345c ")).isTrue();
        assertThat(TanFormat.isValid("BLRA12345C")).isTrue();
    }

    @Test
    void rejectsMalformedTan() {
        assertThat(TanFormat.isValid(null)).isFalse();
        assertThat(TanFormat.isValid("")).isFalse();
        assertThat(TanFormat.isValid("BLRA1234C")).isFalse();
        assertThat(TanFormat.isValid("BLRA123456C")).isFalse();
        assertThat(TanFormat.isValid("BLRAX2345C")).isFalse();
        assertThat(TanFormat.isValid("1LRA12345C")).isFalse();
        assertThat(TanFormat.isValid("BLRA12345")).isFalse();
    }
}
