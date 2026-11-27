package com.arktech.superaccountant.masters.validation;

import com.arktech.superaccountant.statutory.services.StatutorySectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidTdsSectionValidatorTest {

    private ValidTdsSectionValidator validator;

    @BeforeEach
    void setUp() {
        StatutorySectionService sectionService = mock(StatutorySectionService.class);
        when(sectionService.isActiveIncomeTaxSection(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return code != null && java.util.Set.of("194A", "194C", "194H", "194I_A", "194I_B",
                    "194J_A", "194J_B", "194J_C", "194M", "194Q").contains(code);
        });
        validator = new ValidTdsSectionValidator(sectionService);
    }

    @Test
    void acceptsSeededSectionsAndSentinels() {
        for (String ok : new String[] {null, "NOT_SUBJECT", "OTHER", "194A", "194C", "194H", "194I_A",
                "194I_B", "194J_A", "194J_B", "194J_C", "194M", "194Q"}) {
            assertTrue(validator.isValid(ok, null), ok);
        }
    }

    @Test
    void rejectsUnseededAndUnknownCodes() {
        for (String bad : new String[] {"", "194B", "194D", "194I", "194J", "194T", "194Z",
                "194j_a", "Not subject to TDS", "194C ;"}) {
            assertFalse(validator.isValid(bad, null), bad);
        }
    }
}
