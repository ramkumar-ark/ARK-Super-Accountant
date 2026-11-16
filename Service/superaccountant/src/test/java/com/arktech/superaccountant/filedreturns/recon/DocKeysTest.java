package com.arktech.superaccountant.filedreturns.recon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocKeysTest {

    @Test
    void normalisesSeparatorsAndCase() {
        assertThat(DocKeys.normalise(" inv/001 ")).isEqualTo("INV001");
        assertThat(DocKeys.normalise("INV-001_A.B")).isEqualTo("INV001AB");
    }

    @Test
    void looseKeyMatchesSeparatorAndLeadingZeroVariants() {
        assertThat(DocKeys.loose("INV/001")).isEqualTo("INV1");
        assertThat(DocKeys.loose("INV-1")).isEqualTo("INV1");
        assertThat(DocKeys.loose("inv-1")).isEqualTo("INV1");
        assertThat(DocKeys.loose("2026/INV/0007")).isEqualTo("2026INV7");
        assertThat(DocKeys.loose("V100")).isEqualTo("V100");
        assertThat(DocKeys.loose("0")).isEqualTo("0");
        assertThat(DocKeys.loose(null)).isEmpty();
    }
}
