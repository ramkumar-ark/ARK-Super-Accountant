package com.arktech.superaccountant.review;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceHashTest {

    @Test
    void voucherIdentity_usesGuidWhenPresent() {
        assertThat(EvidenceHash.voucherIdentity("g-1", "Sales", "S1", LocalDate.of(2025, 4, 1)))
                .isEqualTo("g-1");
    }

    @Test
    void voucherIdentityFallsBackWithoutGuid() {
        assertThat(EvidenceHash.voucherIdentity("", "Sales", "S1", LocalDate.of(2025, 4, 1)))
                .isEqualTo("Sales|S1|2025-04-01");
    }

    @Test
    void voucherIdentity_blankGuidAndNulls() {
        assertThat(EvidenceHash.voucherIdentity(null, null, null, null)).isEqualTo("||");
    }

    @Test
    void of_joinsFiveFields() {
        assertThat(EvidenceHash.of("T", "id", "e", "a", "h"))
                .isEqualTo(EvidenceHash.of("T", "id", "e", "a", "h"));
        assertThat(EvidenceHash.of("T", "id", "e", "a", "h"))
                .isNotEqualTo(EvidenceHash.of("T", "id", "e", "a", "h2"));
    }

    @Test
    void evidenceHash_changesWithContentHash() {
        String a = EvidenceHash.of("GST_DAYBOOK", "v1", "expected", "actual", "content-1");
        String b = EvidenceHash.of("GST_DAYBOOK", "v1", "expected", "actual", "content-2");
        assertThat(a).isNotEqualTo(b);
        assertThat(a).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void evidenceHash_nullsBecomeEmpty() {
        assertThat(EvidenceHash.of(null, null, null, null, null))
                .isEqualTo(EvidenceHash.of(null, null, null, null, null));
    }
}
