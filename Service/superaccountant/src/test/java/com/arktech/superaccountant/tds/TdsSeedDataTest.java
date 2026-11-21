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
    void seedsAllNineSections() {
        for (String code : new String[]{"194A", "194C", "194H", "194I_A", "194I_B", "194J_A", "194J_B", "194M", "194Q"}) {
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

    @Test
    void s194iSplitsIntoPlantAndBuildingHalves() {
        TdsSectionConfig plant = repo.findBySectionCode("194I_A").orElseThrow();
        // Rs 50,000/month per payee => Rs 6,00,000 annual per payee
        assertEquals(0, new BigDecimal("2.00").compareTo(plant.getRateCompany()));
        assertEquals(0, new BigDecimal("2.00").compareTo(plant.getRateNonCompany()));
        assertEquals(0, new BigDecimal("2.00").compareTo(plant.getNewActRateNonCompany()));
        assertEquals(0, new BigDecimal("600000.00").compareTo(plant.getNewActThresholdAnnual()));

        TdsSectionConfig building = repo.findBySectionCode("194I_B").orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(building.getRateCompany()));
        assertEquals(0, new BigDecimal("10.00").compareTo(building.getRateNonCompany()));
        assertEquals(0, new BigDecimal("10.00").compareTo(building.getNewActRateNonCompany()));
        assertEquals(0, new BigDecimal("600000.00").compareTo(building.getNewActThresholdAnnual()));
    }

    @Test
    void s194jSeedsNewActThreshold50k() {
        TdsSectionConfig jA = repo.findBySectionCode("194J_A").orElseThrow();
        assertEquals(0, new BigDecimal("50000.00").compareTo(jA.getNewActThresholdAnnual()));
        TdsSectionConfig jB = repo.findBySectionCode("194J_B").orElseThrow();
        assertEquals(0, new BigDecimal("50000.00").compareTo(jB.getNewActThresholdAnnual()));
    }

    @Test
    void s194mHasNoNewActValues() {
        assertNull(repo.findBySectionCode("194M").orElseThrow().getNewActRateCompany());
    }
}
