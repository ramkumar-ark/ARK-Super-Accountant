package com.arktech.superaccountant.gst.services;

import com.arktech.superaccountant.masters.util.StandardGstLedgers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GstValidationServiceLedgerNamesTest {

    private final GstValidationService service = new GstValidationService();

    @Test
    void invoiceTaxLedgersAreRecognised() {
        assertTrue(service.isGstLedger("Input CGST A/c"));
        assertTrue(service.isGstLedger("Output SGST A/c"));
        assertTrue(service.isInputGstLedger("Input IGST A/c"));
        assertTrue(service.isOutputGstLedger("Output CGST A/c"));
        assertFalse(service.isInputGstLedger("Output CGST A/c"));
        assertFalse(service.isOutputGstLedger("Input SGST A/c"));
        // legacy pattern never matched Cess names; they must stay non-tax rather than become false positives
        assertFalse(service.isGstLedger("Input Cess A/c"));
        assertFalse(service.isGstLedger("Output Cess A/c"));
    }

    @Test
    void payableCashBalanceAndTdsReceivableAreNotInvoiceTaxLedgers() {
        for (StandardGstLedgers.StandardGstLedger l : StandardGstLedgers.ledgers()) {
            String name = l.ledgerName();
            if (name.contains("SGST") || name.contains("CGST") || name.contains("IGST")) {
                if (name.startsWith("Input ") || name.startsWith("Output ")) {
                    assertTrue(service.isGstLedger(name), name);
                } else {
                    assertFalse(service.isGstLedger(name), name);
                }
            } else {
                // Cess names were never matched by the legacy pattern and must stay that way
                assertFalse(service.isGstLedger(name), name);
            }
            assertFalse(service.isInputGstLedger(name) && !name.startsWith("Input "), name);
            assertFalse(service.isOutputGstLedger(name) && !name.startsWith("Output "), name);
        }
    }

    @Test
    void componentsStillResolveForInvoiceLedgers() {
        assertEquals("IGST", service.getGstComponent("Input IGST A/c"));
        assertEquals("CGST", service.getGstComponent("Output CGST A/c"));
        assertEquals("SGST", service.getGstComponent("Input SGST A/c"));
    }
}
