package com.arktech.superaccountant.tds.rpu;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RpuHeaderTest {

    private static RpuHeader header(int fy, int quarter) {
        return new RpuHeader("TAN", "PAN", "Name", "Addr", "K", "Person", "Director",
                null, fy, quarter, LocalDate.of(2026, 7, 1), false);
    }

    @Test
    void quarterEndDate_isTheLastDayOfEachQuarter() {
        assertThat(header(2026, 1).quarterEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(header(2026, 2).quarterEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(header(2026, 3).quarterEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(header(2026, 4).quarterEndDate()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void quarterEndDate_isNullOutsideQuarters1To4() {
        assertThat(header(2026, 0).quarterEndDate()).isNull();
        assertThat(header(2026, 5).quarterEndDate()).isNull();
    }
}
