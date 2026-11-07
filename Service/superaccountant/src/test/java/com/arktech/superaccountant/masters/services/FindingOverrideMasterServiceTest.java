package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.MasterEditLogRepository;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindingOverrideMasterServiceTest {

    @Mock
    PreconfiguredMasterRepository masterRepository;
    @Mock
    UploadLedgerSnapshotRepository snapshotRepository;
    @Mock
    MasterEditLogRepository editLogRepository;

    @InjectMocks
    FindingOverrideMasterService service;

    private final UUID orgId = UUID.randomUUID();
    private PreconfiguredMaster master;

    @BeforeEach
    void setUp() {
        master = new PreconfiguredMaster();
        master.setId(UUID.randomUUID());
        master.setOrganizationId(orgId);
        master.setLedgerName("Office Rent");
        master.setCategory(LedgerCategory.EXPENSE);
        master.setExpectedParentGroup("Indirect Expenses");
        master.setExpectedGstApplicable(true);
        master.setExpectedTdsApplicable(false);
    }

    private ValidationFinding finding(String ruleCode, MismatchType type, String ledger, String expected, String actual) {
        ValidationFinding f = new ValidationFinding();
        f.setId(UUID.randomUUID());
        f.setUploadJobId(UUID.randomUUID());
        f.setRuleCode(ruleCode);
        f.setMismatchType(type);
        f.setCategory(LedgerCategory.EXPENSE);
        f.setLedgerName(ledger);
        f.setExpectedValue(expected);
        f.setActualValue(actual);
        f.setSubmittedBy("operator1");
        return f;
    }

    private MasterEditLog onlyLog() {
        ArgumentCaptor<MasterEditLog> c = ArgumentCaptor.forClass(MasterEditLog.class);
        verify(editLogRepository).save(c.capture());
        return c.getValue();
    }

    private void masters() {
        when(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).thenReturn(List.of(master));
    }

    @Test
    void apply_nameMismatch_locatesMasterByExpectedValueAndRenamesToActual() {
        masters();
        // ledgerName on this finding is the Tally name, not the master's current name
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.NAME_MISMATCH,
                "office rent ", "Office Rent", "office rent ");

        service.apply(f, orgId, "acct1");

        assertThat(master.getLedgerName()).isEqualTo("office rent ");
        verify(masterRepository).save(master);
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("ledger_name");
        assertThat(log.getOldValue()).isEqualTo("Office Rent");
        assertThat(log.getNewValue()).isEqualTo("office rent ");
        assertThat(log.getEditedBy()).isEqualTo("acct1");
        assertThat(log.getSubmittedBy()).isEqualTo("operator1");
        assertThat(log.getSource()).isEqualTo("FINDING_OVERRIDE");
        assertThat(log.getFindingId()).isEqualTo(f.getId());
        assertThat(log.getMasterId()).isEqualTo(master.getId());
    }

    @Test
    void apply_staleNameMismatch_masterRenamedSinceSubmission_throws409() {
        master.setLedgerName("Office Rent Renamed");
        masters();
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.NAME_MISMATCH,
                "office rent", "Office Rent", "office rent");

        assertThatThrownBy(() -> service.apply(f, orgId, "acct1"))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(masterRepository, never()).save(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void apply_parentGroupMismatch_writesActualValue() {
        masters();
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.PARENT_GROUP_MISMATCH,
                "OFFICE RENT", "Indirect Expenses", "Direct Expenses");

        service.apply(f, orgId, "acct1");

        assertThat(master.getExpectedParentGroup()).isEqualTo("Direct Expenses");
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("expected_parent_group");
        assertThat(log.getOldValue()).isEqualTo("Indirect Expenses");
        assertThat(log.getNewValue()).isEqualTo("Direct Expenses");
    }

    @Test
    void apply_gstApplicabilityMismatch_parsesBooleanFromActualValue() {
        masters();
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.GST_APPLICABILITY_MISMATCH,
                "Office Rent", "true", "false");

        service.apply(f, orgId, "acct1");

        assertThat(master.getExpectedGstApplicable()).isFalse();
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("expected_gst_applicable");
        assertThat(log.getOldValue()).isEqualTo("true");
        assertThat(log.getNewValue()).isEqualTo("false");
    }

    @Test
    void apply_tdsApplicabilityMismatch_parsesBooleanFromActualValue() {
        masters();
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.TDS_APPLICABILITY_MISMATCH,
                "Office Rent", "false", "true");

        service.apply(f, orgId, "acct1");

        assertThat(master.getExpectedTdsApplicable()).isTrue();
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("expected_tds_applicable");
        assertThat(log.getOldValue()).isEqualTo("false");
        assertThat(log.getNewValue()).isEqualTo("true");
    }

    @Test
    void apply_missingInConfiguration_createsMasterFromSnapshotAndLogsEachSeededField() {
        when(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).thenReturn(List.of());
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName("New Vendor");
        s.setParentGroup("Sundry Creditors");
        s.setGstApplicable(true);
        s.setTdsApplicable(false);
        s.setGstin("29ABCDE1234F1Z5");
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "new vendor", null, "new vendor");
        f.setCategory(LedgerCategory.CREDITOR);
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(f.getUploadJobId(), "new vendor"))
                .thenReturn(List.of(s));
        when(masterRepository.save(any(PreconfiguredMaster.class))).thenAnswer(i -> i.getArgument(0));

        service.apply(f, orgId, "acct1");

        ArgumentCaptor<PreconfiguredMaster> m = ArgumentCaptor.forClass(PreconfiguredMaster.class);
        verify(masterRepository).save(m.capture());
        assertThat(m.getValue().getOrganizationId()).isEqualTo(orgId);
        assertThat(m.getValue().getLedgerName()).isEqualTo("New Vendor");
        assertThat(m.getValue().getCategory()).isEqualTo(LedgerCategory.CREDITOR);
        assertThat(m.getValue().getExpectedParentGroup()).isEqualTo("Sundry Creditors");
        assertThat(m.getValue().getGstin()).isEqualTo("29ABCDE1234F1Z5");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MasterEditLog>> logs = ArgumentCaptor.forClass(List.class);
        verify(editLogRepository).saveAll(logs.capture());
        // only non-null seeded fields: name, parent group, gst, tds, gstin
        assertThat(logs.getValue()).extracting(MasterEditLog::getFieldName)
                .containsExactlyInAnyOrder("ledger_name", "expected_parent_group",
                        "expected_gst_applicable", "expected_tds_applicable", "gstin");
        assertThat(logs.getValue()).allSatisfy(l -> {
            assertThat(l.getOldValue()).isNull();
            assertThat(l.getEditedBy()).isEqualTo("acct1");
            assertThat(l.getSubmittedBy()).isEqualTo("operator1");
        });
    }

    @Test
    void apply_withSharedMasterIndex_seesMastersCreatedEarlierInTheBatch_withoutReloading() {
        when(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).thenReturn(List.of(master));
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName("New Vendor");
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(any(), anyString())).thenReturn(List.of(s));
        when(masterRepository.save(any(PreconfiguredMaster.class))).thenAnswer(i -> i.getArgument(0));
        ValidationFinding first = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "new vendor", null, "new vendor");
        ValidationFinding duplicate = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "New Vendor ", null, "New Vendor ");
        ValidationFinding parent = finding("MISMATCH_DETECTION", MismatchType.PARENT_GROUP_MISMATCH,
                "office rent", "Indirect Expenses", "Direct Expenses");

        Map<String, PreconfiguredMaster> index = service.activeMastersByName(orgId);
        service.apply(first, orgId, "acct1", index);
        service.apply(parent, orgId, "acct1", index);

        assertThat(master.getExpectedParentGroup()).isEqualTo("Direct Expenses");
        assertThatThrownBy(() -> service.apply(duplicate, orgId, "acct1", index))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(masterRepository, org.mockito.Mockito.times(1)).findByOrganizationIdAndActiveTrue(orgId);
    }

    @Test
    void apply_withSharedMasterIndex_renamedMasterIsFoundByItsNewName() {
        when(masterRepository.findByOrganizationIdAndActiveTrue(orgId)).thenReturn(List.of(master));
        ValidationFinding rename = finding("MISMATCH_DETECTION", MismatchType.NAME_MISMATCH,
                "Office Rent Paid", "Office Rent", "Office Rent Paid");
        ValidationFinding parent = finding("MISMATCH_DETECTION", MismatchType.PARENT_GROUP_MISMATCH,
                "office rent paid", "Indirect Expenses", "Direct Expenses");

        Map<String, PreconfiguredMaster> index = service.activeMastersByName(orgId);
        service.apply(rename, orgId, "acct1", index);
        service.apply(parent, orgId, "acct1", index);

        assertThat(master.getLedgerName()).isEqualTo("Office Rent Paid");
        assertThat(master.getExpectedParentGroup()).isEqualTo("Direct Expenses");
        assertThat(index).containsOnlyKeys("office rent paid");
    }

    @Test
    void apply_missingInConfiguration_noSnapshot_throws409() {
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "new vendor", null, "new vendor");
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(any(), anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service.apply(f, orgId, "acct1"))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(masterRepository, never()).save(any());
        verify(editLogRepository, never()).saveAll(anyList());
    }

    @Test
    void apply_gstin_writesProposedValue() {
        masters();
        ValidationFinding f = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        f.setProposedValue(" 29ABCDE1234F1Z5 ");

        service.apply(f, orgId, "acct1");

        assertThat(master.getGstin()).isEqualTo("29ABCDE1234F1Z5");
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("gstin");
        assertThat(log.getOldValue()).isNull();
        assertThat(log.getNewValue()).isEqualTo("29ABCDE1234F1Z5");
    }

    @Test
    void apply_hsnSac_writesProposedValue() {
        masters();
        ValidationFinding f = finding("HSN_SAC_CODE", null, "Office Rent", null, null);
        f.setProposedValue("997212");

        service.apply(f, orgId, "acct1");

        assertThat(master.getHsnSacCode()).isEqualTo("997212");
        assertThat(onlyLog().getFieldName()).isEqualTo("hsn_sac_code");
    }

    @Test
    void apply_tdsSection_writesProposedValue() {
        masters();
        ValidationFinding f = finding("TDS_SECTION_MAPPING", null, "Office Rent", null, null);
        f.setProposedValue("194I");

        service.apply(f, orgId, "acct1");

        assertThat(master.getTdsSection()).isEqualTo("194I");
        assertThat(onlyLog().getFieldName()).isEqualTo("tds_section");
    }

    @Test
    void apply_gstApplicability_writesEnumValue() {
        masters();
        ValidationFinding f = finding("GST_APPLICABILITY", null, "Office Rent", null, null);
        f.setProposedValue("RCM");

        service.apply(f, orgId, "acct1");

        assertThat(master.getGstApplicabilityType()).isEqualTo(GstApplicabilityType.RCM);
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("gst_applicability_type");
        assertThat(log.getNewValue()).isEqualTo("RCM");
    }

    @Test
    void apply_blankProposedValueOnTypedRule_throws400AndWritesNothing() {
        ValidationFinding f = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        f.setProposedValue("  ");

        assertThatThrownBy(() -> service.apply(f, orgId, "acct1"))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(masterRepository, never()).save(any());
        verifyNoInteractions(editLogRepository);
    }

    @Test
    void apply_masterOfAnotherOrganization_isNeverTouched() {
        UUID otherOrg = UUID.randomUUID();
        when(masterRepository.findByOrganizationIdAndActiveTrue(otherOrg)).thenReturn(List.of());
        ValidationFinding f = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        f.setProposedValue("29ABCDE1234F1Z5");

        assertThatThrownBy(() -> service.apply(f, otherOrg, "acct1"))
                .isInstanceOf(FindingOverrideException.class);
        assertThat(master.getGstin()).isNull();
        verify(masterRepository, never()).save(any());
    }

    @Test
    void validate_typedRuleWithBlankValue_throws400() {
        for (String rule : List.of("GSTIN_PRESENCE", "HSN_SAC_CODE", "TDS_SECTION_MAPPING", "GST_APPLICABILITY")) {
            ValidationFinding f = finding(rule, null, "Office Rent", null, null);
            assertThatThrownBy(() -> service.validate(f, null, orgId))
                    .as(rule)
                    .isInstanceOfSatisfying(FindingOverrideException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> service.validate(f, "   ", orgId))
                    .as(rule)
                    .isInstanceOf(FindingOverrideException.class);
        }
    }

    @Test
    void validate_gstApplicabilityWithUnknownConstant_throws400ListingAllowedValues() {
        ValidationFinding f = finding("GST_APPLICABILITY", null, "Office Rent", null, null);

        assertThatThrownBy(() -> service.validate(f, "BOGUS", orgId))
                .isInstanceOfSatisfying(FindingOverrideException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("TAXABLE", "EXEMPT", "ZERO_RATED", "NON_GST", "RCM", "NOT_APPLICABLE");
                });
    }

    @Test
    void validate_gstApplicabilityWithValidConstant_passes() {
        ValidationFinding f = finding("GST_APPLICABILITY", null, "Office Rent", null, null);
        service.validate(f, "EXEMPT", orgId);
    }

    @Test
    void validate_autoDerivedMismatch_needsNoValue() {
        for (MismatchType t : List.of(MismatchType.NAME_MISMATCH, MismatchType.PARENT_GROUP_MISMATCH)) {
            service.validate(finding("MISMATCH_DETECTION", t, "Office Rent", "a", "b"), null, orgId);
        }
        for (MismatchType t : List.of(MismatchType.GST_APPLICABILITY_MISMATCH, MismatchType.TDS_APPLICABILITY_MISMATCH)) {
            service.validate(finding("MISMATCH_DETECTION", t, "Office Rent", "true", "false"), null, orgId);
        }
    }

    @Test
    void applicabilityMismatch_withAbsentTallyFlag_throws400AndWritesNothing() {
        for (MismatchType t : List.of(MismatchType.GST_APPLICABILITY_MISMATCH, MismatchType.TDS_APPLICABILITY_MISMATCH)) {
            ValidationFinding f = finding("MISMATCH_DETECTION", t, "Office Rent", "true", "null");
            assertThatThrownBy(() -> service.validate(f, null, orgId))
                    .as(t.name())
                    .isInstanceOfSatisfying(FindingOverrideException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> service.apply(f, orgId, "acct1"))
                    .as(t.name())
                    .isInstanceOfSatisfying(FindingOverrideException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(masterRepository, never()).save(any());
        verifyNoInteractions(editLogRepository);
    }

    @Test
    void validate_typedRuleWithMalformedValue_throws400() {
        Map<String, String> malformed = Map.of(
                "GSTIN_PRESENCE", "abc",
                "HSN_SAC_CODE", "12",
                "TDS_SECTION_MAPPING", "195X");
        malformed.forEach((rule, value) -> {
            ValidationFinding f = finding(rule, null, "Office Rent", null, null);
            assertThatThrownBy(() -> service.validate(f, value, orgId))
                    .as(rule)
                    .isInstanceOfSatisfying(FindingOverrideException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        });
    }

    @Test
    void apply_malformedGstin_throws400AndWritesNothing() {
        ValidationFinding f = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        f.setProposedValue("abc");

        assertThatThrownBy(() -> service.apply(f, orgId, "acct1"))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(master.getGstin()).isNull();
        verify(masterRepository, never()).save(any());
        verifyNoInteractions(editLogRepository);
    }

    @Test
    void validate_missingInConfigurationWithoutSnapshot_throws409() {
        ValidationFinding f = finding("MISMATCH_DETECTION", MismatchType.MISSING_IN_CONFIGURATION,
                "new vendor", null, "new vendor");
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(any(), anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service.validate(f, null, orgId))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void validate_unmappedFindingType_throws400() {
        ValidationFinding f = finding("SOMETHING_ELSE", null, "x", null, null);
        assertThatThrownBy(() -> service.validate(f, "v", orgId))
                .isInstanceOfSatisfying(FindingOverrideException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void apply_gstinPresence_notApplicable_marksGstNotApplicableAndAudits() {
        masters();
        ValidationFinding f = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        f.setProposedValue("NOT_APPLICABLE");

        service.apply(f, orgId, "acct1");

        assertThat(master.getGstApplicabilityType()).isEqualTo(GstApplicabilityType.NOT_APPLICABLE);
        assertThat(master.getGstin()).isNull();
        MasterEditLog log = onlyLog();
        assertThat(log.getFieldName()).isEqualTo("gst_applicability_type");
        assertThat(log.getNewValue()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void validate_gstinPresence_acceptsNotApplicableMarker_butStillRejectsJunk() {
        ValidationFinding ok = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        service.validate(ok, "NOT_APPLICABLE", orgId);

        ValidationFinding bad = finding("GSTIN_PRESENCE", null, "Office Rent", null, null);
        assertThatThrownBy(() -> service.validate(bad, "NOT APPLICABLE", orgId))
                .isInstanceOf(FindingOverrideException.class);
    }
}
