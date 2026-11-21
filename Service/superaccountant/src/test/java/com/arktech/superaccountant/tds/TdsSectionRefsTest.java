package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.tds.models.TdsSectionRefs;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TdsSectionRefsTest {

    @Test
    void newActRefKnowsMappedSectionsAndIgnoresOthers() {
        assertEquals("393(1) [Table: Sl. No. 6(i)]", TdsSectionRefs.newActRef("194C"));
        assertEquals("393(1) [Table: Sl. No. 2(ii).D(b)]", TdsSectionRefs.newActRef("194I"));
        assertEquals("393(1) [Table: Sl. No. 5(ii) and 5(iii)]", TdsSectionRefs.newActRef("194A"));
        assertEquals("393(1) [Table: Sl. No. 6(iii).D(a)]", TdsSectionRefs.newActRef("194J_A"));
        assertEquals("393(1) [Table: Sl. No. 6(iii).D(b)]", TdsSectionRefs.newActRef("194J_B"));
        assertEquals("393(1) [Table: Sl. No. 1(ii)]", TdsSectionRefs.newActRef("194H"));
        assertEquals("393(1) [Table: Sl. No. 8(ii)]", TdsSectionRefs.newActRef("194Q"));
        assertEquals("393(1) [Table: Sl. No. 2(ii).D(a)]", TdsSectionRefs.newActRef("194I_A"));
        assertEquals("393(1) [Table: Sl. No. 2(ii).D(b)]", TdsSectionRefs.newActRef("194I_B"));
        assertEquals("393(1) [Table: Sl. No. 6(iii).D(b)]", TdsSectionRefs.newActRef("194J_C"));
        assertNull(TdsSectionRefs.newActRef("194M"));
        assertNull(TdsSectionRefs.newActRef(null));
    }

    @Test
    void forDateUsesNewRefOnOrAfterFirstApril2026() {
        assertEquals("393(1) [Table: Sl. No. 6(i)]",
                TdsSectionRefs.forDate("194C", LocalDate.of(2026, 4, 1)));
        assertEquals("393(1) [Table: Sl. No. 6(i)]",
                TdsSectionRefs.forDate("194C", LocalDate.of(2027, 1, 1)));
    }

    @Test
    void forDateKeepsOldSectionBeforeEffectiveDateOrWithoutDate() {
        assertEquals("194C", TdsSectionRefs.forDate("194C", LocalDate.of(2026, 3, 31)));
        assertEquals("194C", TdsSectionRefs.forDate("194C", null));
    }

    @Test
    void forDateFallsBackToCodeForSectionsWithoutNewActRef() {
        assertEquals("194M", TdsSectionRefs.forDate("194M", LocalDate.of(2026, 6, 15)));
    }

    @Test
    void bothShowsOldFirstThenNewRef() {
        assertEquals("194C / 393(1) [Table: Sl. No. 6(i)]", TdsSectionRefs.both("194C"));
        assertEquals("194M", TdsSectionRefs.both("194M"));
        assertNull(TdsSectionRefs.both(null));
    }
}
