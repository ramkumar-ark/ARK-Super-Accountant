package com.arktech.superaccountant.masters.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GstRegistrationTypesTest {

    @Test
    void isRegistered_variants() {
        assertThat(GstRegistrationTypes.isRegistered("Regular")).isTrue();
        assertThat(GstRegistrationTypes.isRegistered("  regular ")).isTrue();
        assertThat(GstRegistrationTypes.isRegistered("Composition")).isTrue();
        assertThat(GstRegistrationTypes.isRegistered(null)).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("   ")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("Unregistered")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("UNREGISTERED")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("Unregistered/Consumer")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered("Consumer")).isFalse();
        assertThat(GstRegistrationTypes.isRegistered(" Unknown ")).isFalse();
    }
}
