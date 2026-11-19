package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportingClassifierTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 3);

    private ReportingClassifier classifier(
            Map<String, Optional<String>> fillIns) {
        return new ReportingClassifier((v, attribute) ->
                fillIns.getOrDefault(attribute.name(), Optional.empty()));
    }

    private ReportingClassifier classifier() {
        return classifier(Map.of());
    }

    private VoucherView.Builder voucher() {
        return new VoucherView.Builder()
                .supplierStateCode("29")
                .placeOfSupply("24")
                .totalValue(new BigDecimal("50000.00"));
    }

    @Test
    void exactlyThresholdGoesToB2cs() {
        VoucherView v = voucher()
                .totalValue(new BigDecimal("250000.00"))
                .build();
        assertEquals(ReportingClass.B2CS, classifier().classify(v, PERIOD).category());
    }

    @Test
    void justAboveThresholdInterStateGoesToB2cl() {
        VoucherView v = voucher()
                .totalValue(new BigDecimal("250000.01"))
                .build();
        assertEquals(ReportingClass.B2CL, classifier().classify(v, PERIOD).category());
    }

    @Test
    void intraStateUnregisteredAboveThresholdGoesToB2cs() {
        VoucherView v = voucher()
                .placeOfSupply("29")
                .totalValue(new BigDecimal("300000.00"))
                .build();
        assertEquals(ReportingClass.B2CS, classifier().classify(v, PERIOD).category());
    }

    @Test
    void intraStateNameFormPosMatchesCodeFormResult() {
        VoucherView v = voucher()
                .placeOfSupply("Karnataka")
                .totalValue(new BigDecimal("300000.00"))
                .build();
        assertEquals(ReportingClass.B2CS, classifier().classify(v, PERIOD).category());
    }

    @Test
    void intraStatePrefixedPosMatchesCodeFormResult() {
        VoucherView v = voucher()
                .placeOfSupply("29-Maharashtra")
                .totalValue(new BigDecimal("300000.00"))
                .build();
        assertEquals(ReportingClass.B2CS, classifier().classify(v, PERIOD).category());
    }

    @Test
    void interStatePrefixedPosMatchesCodeFormResult() {
        VoucherView v = voucher()
                .placeOfSupply("27-Maharashtra")
                .totalValue(new BigDecimal("300000.00"))
                .build();
        assertEquals(ReportingClass.B2CL, classifier().classify(v, PERIOD).category());
    }

    @Test
    void gstinPresentIsB2bRegardlessOfValue() {
        VoucherView v = voucher()
                .partyGstin("29ABCDE1234F1Z5")
                .placeOfSupply("29")
                .totalValue(new BigDecimal("9999.00"))
                .build();
        assertEquals(ReportingClass.B2B, classifier().classify(v, PERIOD).category());
    }

    @Test
    void unregisteredCreditNoteIsCdnur() {
        VoucherView v = voucher()
                .voucherTypeName("Credit Note")
                .totalValue(new BigDecimal("15000.00"))
                .build();
        assertEquals(ReportingClass.CDNUR, classifier().classify(v, PERIOD).category());
    }

    @Test
    void missingPlaceOfSupplyIsNotReportedWithReason() {
        VoucherView v = voucher()
                .placeOfSupply(null)
                .build();
        ReportingClassifier.Result result = classifier().classify(v, PERIOD);
        assertEquals(ReportingClass.NOT_REPORTED, result.category());
        assertNotNull(result.reason());
        assertEquals(false, result.reason().isBlank());
    }

    @Test
    void exportViaFillInIsExp() {
        VoucherView v = voucher()
                .consigneeCountry("United States")
                .totalValue(new BigDecimal("400000.00"))
                .build();
        ReportingClassifier clf = classifier(Map.of(
                "SUPPLY_NATURE", Optional.of("Export")));
        assertEquals(ReportingClass.EXP, clf.classify(v, PERIOD).category());
    }

    @Test
    void exportViaCapturedSupplyNatureIsExp() {
        VoucherView v = voucher()
                .supplyNature("Export")
                .consigneeCountry("United States")
                .totalValue(new BigDecimal("400000.00"))
                .build();
        assertEquals(ReportingClass.EXP, classifier().classify(v, PERIOD).category());
    }

    @Test
    void registeredCreditNoteIsCdnr() {
        VoucherView v = voucher()
                .voucherTypeName("Credit Note")
                .partyGstin("24AACCA5678G1P2")
                .totalValue(new BigDecimal("15000.00"))
                .build();
        assertEquals(ReportingClass.CDNUR == classifier().classify(v, PERIOD).category(),
                false);
        assertEquals(ReportingClass.CDNR, classifier().classify(v, PERIOD).category());
    }
}
