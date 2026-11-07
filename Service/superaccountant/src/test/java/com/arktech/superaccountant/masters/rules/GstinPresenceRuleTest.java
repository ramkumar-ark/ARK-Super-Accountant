package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GstinPresenceRuleTest {

    private GstinPresenceRule rule;

    @BeforeEach
    void setUp() {
        rule = new GstinPresenceRule();
    }

    private PreconfiguredMaster configured(String name, LedgerCategory category, String gstin) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(UUID.randomUUID());
        m.setLedgerName(name);
        m.setCategory(category);
        m.setGstin(gstin);
        m.setActive(true);
        m.setGstApplicabilityType(GstApplicabilityType.TAXABLE);
        m.setGstRegistrationType("Regular");
        if (category == LedgerCategory.CREDITOR) {
            m.setExpectedParentGroup("Sundry Creditors");
        }
        return m;
    }

    private ValidationContext ctx(List<PreconfiguredMaster> masters) {
        return new ValidationContext(UUID.randomUUID(), "testuser", masters, Map.of());
    }

    @Test
    void creditorCategoryNullGstin_emitsHighFinding() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.HIGH, findings.get(0).getSeverity());
    }

    @Test
    void purchaseCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Cement Purchase", LedgerCategory.PURCHASE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void expenseCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Labour Charges", LedgerCategory.EXPENSE, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void incomeCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Sales Account", LedgerCategory.INCOME, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void gstCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Output CGST @18%", LedgerCategory.GST, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void tdsCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("TDS Payable 194C", LedgerCategory.TDS, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void otherCategoryNullGstin_noFinding() {
        PreconfiguredMaster m = configured("Miscellaneous", LedgerCategory.OTHER, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void creditorWithGstinSet_noFinding() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, "29ABCDE1234F1Z5");
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void emptyMastersList_noFindings() {
        List<ValidationFinding> findings = rule.execute(ctx(List.of()), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void findingHasCorrectRuleCode() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("GSTIN_PRESENCE", findings.get(0).getRuleCode());
    }

    @Test
    void findingHasResolveStatusOpen() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals(ResolveStatus.OPEN, findings.get(0).getResolveStatus());
    }

    @Test
    void findingHasLedgerName() {
        PreconfiguredMaster m = configured("ABC Traders", LedgerCategory.CREDITOR, null);
        List<ValidationFinding> findings = rule.execute(ctx(List.of(m)), List.of());
        assertEquals("ABC Traders", findings.get(0).getLedgerName());
    }

    private PreconfiguredMaster party(String name, String chain, String registrationType, String gstin) {
        PreconfiguredMaster m = configured(name, LedgerCategory.OTHER, gstin);
        m.setGroupChain(chain);
        m.setGstRegistrationType(registrationType);
        return m;
    }

    private int count(PreconfiguredMaster m) {
        return rule.execute(ctx(List.of(m)), List.of()).size();
    }

    @Test
    void inScopeGroups_registeredWithoutGstin_areFlagged() {
        assertEquals(1, count(party("D", "sundry debtors", "Regular", null)));
        assertEquals(1, count(party("C", "sundry creditors", "Composition", null)));
        assertEquals(1, count(party("L1", "loans & advances (asset)", "Regular", null)));
        assertEquals(1, count(party("L2", "secured loans", "Regular", null)));
        assertEquals(1, count(party("L3", "unsecured loans", "Regular", null)));
    }

    @Test
    void customSubGroup_isFoundThroughTheParentChain() {
        assertEquals(1, count(party("Vendor", "south vendors|regional vendors|sundry creditors", "Regular", null)));
    }

    @Test
    void unregisteredConsumerUnknownOrBlankRegistration_notFlagged() {
        assertEquals(0, count(party("a", "sundry creditors", "Unregistered/Consumer", null)));
        assertEquals(0, count(party("b", "sundry debtors", "Unregistered", null)));
        assertEquals(0, count(party("c", "sundry debtors", "Consumer", null)));
        assertEquals(0, count(party("d", "sundry creditors", "Unknown", null)));
        assertEquals(0, count(party("e", "sundry creditors", "", null)));
        assertEquals(0, count(party("f", "sundry creditors", null, null)));
    }

    @Test
    void outOfScopeGroups_notFlagged() {
        assertEquals(0, count(party("Sales", "sales accounts", "Regular", null)));
        assertEquals(0, count(party("Cash", "cash-in-hand", "Regular", null)));
        assertEquals(0, count(party("Plant", "fixed assets", "Regular", null)));
    }

    @Test
    void gstinPresent_notFlagged() {
        assertEquals(0, count(party("C", "sundry creditors", "Regular", "29ABCDE1234F1Z5")));
    }

    @Test
    void markedGstNotApplicable_notFlagged() {
        PreconfiguredMaster m = party("C", "sundry creditors", "Regular", null);
        m.setGstApplicabilityType(GstApplicabilityType.NOT_APPLICABLE);
        assertEquals(0, count(m));
    }

    @Test
    void getRuleCode_returnsGstinPresence() {
        assertEquals("GSTIN_PRESENCE", rule.getRuleCode());
    }
}
