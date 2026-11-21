package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.ValidationFinding;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class MastersFindingApplierTest {

    private static final UUID ORG = UUID.randomUUID();
    private final FindingOverrideMasterService masterSvc = mock(FindingOverrideMasterService.class);
    private final PanFormatOverrideService panSvc = mock(PanFormatOverrideService.class);
    private final MastersFindingApplier applier =
            new MastersFindingApplier(masterSvc, panSvc, ORG, null, null);

    @Test
    void panFormatValidateAndApplyGoToPanServiceOnly() {
        ValidationFinding f = new ValidationFinding();
        f.setRuleCode("PAN_FORMAT");
        f.setLedgerName("ANANDHA TIMBER DEPOT");

        applier.validate(f, "AAPPK2262D", "");
        applier.apply(f, "acct");

        verify(panSvc).validate("AAPPK2262D");
        verify(panSvc).apply(f, ORG, "acct");
        verifyNoInteractions(masterSvc);
    }

    @Test
    void otherRulesStillDelegateToMasterService() {
        ValidationFinding f = new ValidationFinding();
        f.setRuleCode("GSTIN_PRESENCE");

        applier.validate(f, "27AAAAA0000A1Z5", "");
        applier.apply(f, "acct");

        verify(masterSvc).validate(f, "27AAAAA0000A1Z5", ORG, null);
        verify(masterSvc).apply(f, ORG, "acct");
        verifyNoInteractions(panSvc);
    }
}
