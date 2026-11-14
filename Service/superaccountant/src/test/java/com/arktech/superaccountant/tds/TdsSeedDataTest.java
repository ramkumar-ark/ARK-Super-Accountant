package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsSeedDataTest {

    @Autowired
    private TdsSectionConfigRepository repo;

    @Test
    void seedsAllEightSections() {
        for (String code : new String[]{"194A", "194C", "194H", "194I", "194J_A", "194J_B", "194M", "194Q"}) {
            assertTrue(repo.existsBySectionCode(code), "missing seed: " + code);
        }
    }

    @Test
    void s194cHasDualThresholdAndSplitRates() {
        TdsSectionConfig c = repo.findBySectionCode("194C").orElseThrow();
        assertEquals(0, new BigDecimal("30000.00").compareTo(c.getThresholdPerTransaction()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(c.getThresholdAnnual()));
        assertEquals(0, new BigDecimal("2.00").compareTo(c.getRateCompany()));
        assertEquals(0, new BigDecimal("1.00").compareTo(c.getRateNonCompany()));
    }
}
