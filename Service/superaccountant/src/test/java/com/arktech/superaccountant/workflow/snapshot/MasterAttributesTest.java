package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.scope.MastersScope;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MasterAttributesTest {

    private UploadLedgerSnapshot snapshot(LedgerCategory category) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName("Acme Traders");
        s.setCategory(category);
        s.setGstin("29ABCDE1234F1Z5");
        s.setGstRegistrationType("REGULAR");
        s.setGstApplicable(true);
        s.setGstApplicabilityType(GstApplicabilityType.TAXABLE);
        s.setHsnSacCode("1234");
        s.setPan("ABCDE1234F");
        s.setTdsSection("194C");
        s.setTdsApplicable(true);
        return s;
    }

    @Test
    void gstTypes_freezeGstinRegistrationApplicabilityHsnCategory() {
        Map<String, String> values = MasterAttributes.valuesFor(WorkflowType.GSTR1, snapshot(LedgerCategory.INCOME));
        assertThat(values.keySet()).containsExactlyInAnyOrder(
                "category", "gstin", "gstRegistrationType", "gstApplicable", "gstApplicabilityType", "hsnSacCode");
        assertThat(values.get("gstin")).isEqualTo("29ABCDE1234F1Z5");
        assertThat(values.get("gstApplicable")).isEqualTo("true");
        assertThat(values.get("gstApplicabilityType")).isEqualTo("TAXABLE");
        assertThat(values.get("category")).isEqualTo("INCOME");
    }

    @Test
    void tdsTypes_freezePanSectionApplicableCategory() {
        Map<String, String> values = MasterAttributes.valuesFor(WorkflowType.TDS_26Q, snapshot(LedgerCategory.CREDITOR));
        assertThat(values.keySet()).containsExactlyInAnyOrder("category", "pan", "tdsSection", "tdsApplicable");
        assertThat(values.get("pan")).isEqualTo("ABCDE1234F");
        assertThat(values.get("tdsSection")).isEqualTo("194C");
        assertThat(values.get("tdsApplicable")).isEqualTo("true");
    }

    @Test
    void blankStringsBecomeNull() {
        UploadLedgerSnapshot s = snapshot(LedgerCategory.INCOME);
        s.setGstin("  ");
        assertThat(MasterAttributes.valuesFor(WorkflowType.GSTR1, s).get("gstin")).isNull();
    }

    @Test
    void typesForLedger_categoryMoveIsScopedUnderOldAndNew() {
        UploadLedgerSnapshot previous = snapshot(LedgerCategory.INCOME);
        UploadLedgerSnapshot latest = snapshot(LedgerCategory.PURCHASE);
        assertThat(MastersScope.typesForLedger(latest, previous))
                .containsExactlyInAnyOrder(WorkflowType.GSTR1, WorkflowType.GSTR3B);
    }

    @Test
    void typesForLedger_creditorWithTdsSection_includesTdsTypes() {
        UploadLedgerSnapshot previous = snapshot(LedgerCategory.CREDITOR);
        UploadLedgerSnapshot latest = snapshot(LedgerCategory.CREDITOR);
        assertThat(MastersScope.typesForLedger(latest, previous))
                .containsExactlyInAnyOrder(WorkflowType.GSTR3B, WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q);
    }

    @Test
    void typesForLedger_other_isEmpty() {
        UploadLedgerSnapshot latest = snapshot(LedgerCategory.OTHER);
        UploadLedgerSnapshot previous = snapshot(LedgerCategory.OTHER);
        assertThat(MastersScope.typesForLedger(latest, previous)).isEmpty();
    }
}
