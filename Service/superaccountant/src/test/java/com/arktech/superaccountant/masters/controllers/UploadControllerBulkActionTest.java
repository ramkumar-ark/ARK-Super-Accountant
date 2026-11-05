package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.payload.request.BulkFindingActionRequest;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.services.BulkFindingActionException;
import com.arktech.superaccountant.masters.services.FindingOverrideException;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Role and state rules of the bulk action endpoint; rollback itself is covered in FindingOverrideWorkflowIT. */
@ExtendWith(MockitoExtension.class)
class UploadControllerBulkActionTest {

    @Mock
    UploadJobRepository uploadJobRepository;
    @Mock
    ValidationFindingRepository findingRepository;
    @Mock
    FindingOverrideMasterService overrideService;

    @InjectMocks
    UploadController controller;

    private final UUID orgId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().doCallRealMethod().when(overrideService).submit(any(), any(), any(), any(), any(), any());
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        lenient().when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
    }

    private UserDetailsImpl user(String name, String role) {
        return new UserDetailsImpl(1L, name, name + "@t.com", "x", orgId, List.of(new SimpleGrantedAuthority(role)));
    }

    private ValidationFinding finding(String ledger, ResolveStatus status) {
        ValidationFinding f = new ValidationFinding();
        f.setId(UUID.randomUUID());
        f.setUploadJobId(jobId);
        f.setLedgerName(ledger);
        f.setRuleCode("MISMATCH_DETECTION");
        f.setMismatchType(MismatchType.MISSING_IN_CONFIGURATION);
        f.setResolveStatus(status);
        return f;
    }

    private BulkFindingActionRequest filterReq(String action) {
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setAction(action);
        r.setMismatchType(MismatchType.MISSING_IN_CONFIGURATION);
        return r;
    }

    @Test
    void operatorBulkOverride_submitsEveryMatchingFindingForReview_masterUntouched() {
        ValidationFinding a = finding("A", ResolveStatus.OPEN);
        ValidationFinding b = finding("B", ResolveStatus.OPEN);
        when(findingRepository.findMatchingForUpdate(jobId, ResolveStatus.OPEN, null, null, null,
                MismatchType.MISSING_IN_CONFIGURATION)).thenReturn(List.of(a, b));

        ResponseEntity<?> res = controller.bulkFindingAction(jobId, filterReq("ACKNOWLEDGE"), user("op", "ROLE_OPERATOR"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) res.getBody()).get("processed")).isEqualTo(2);
        assertThat(a.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(b.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(a.getSubmittedBy()).isEqualTo("op");
        verify(overrideService, never()).apply(any(), any(), anyString(), any());
    }

    @Test
    void bulkOverride_byFilter_skipsFindingsThatNeedATypedValueOrCannotBeOverridden() {
        ValidationFinding derivable = finding("A", ResolveStatus.OPEN);
        ValidationFinding typed = finding("Typed", ResolveStatus.OPEN);
        typed.setRuleCode("GSTIN_PRESENCE");
        typed.setMismatchType(null);
        ValidationFinding missingInUpload = finding("Gone", ResolveStatus.OPEN);
        missingInUpload.setMismatchType(MismatchType.MISSING_IN_UPLOAD);
        ValidationFinding absentFlag = finding("NoFlag", ResolveStatus.OPEN);
        absentFlag.setMismatchType(MismatchType.GST_APPLICABILITY_MISMATCH);
        absentFlag.setActualValue("null");
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setSeverity(FindingSeverity.MEDIUM);
        when(findingRepository.findMatchingForUpdate(jobId, ResolveStatus.OPEN, null, FindingSeverity.MEDIUM, null, null))
                .thenReturn(List.of(derivable, typed, missingInUpload, absentFlag));

        ResponseEntity<?> res = controller.bulkFindingAction(jobId, r, user("op", "ROLE_OPERATOR"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) res.getBody()).get("processed")).isEqualTo(1);
        assertThat(derivable.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(typed.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(missingInUpload.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        verify(overrideService, never()).validate(eq(typed), any(), any());
        verify(overrideService, never()).validate(eq(missingInUpload), any(), any());
        verify(overrideService, never()).validate(eq(absentFlag), any(), any());
    }

    @Test
    void bulkOverride_byFilter_onlyNonDerivableFindingsMatch_returns400() {
        ValidationFinding typed = finding("Typed", ResolveStatus.OPEN);
        typed.setRuleCode("HSN_SAC_CODE");
        typed.setMismatchType(null);
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setRuleCode("HSN_SAC_CODE");
        when(findingRepository.findMatchingForUpdate(jobId, ResolveStatus.OPEN, null, null, "HSN_SAC_CODE", null))
                .thenReturn(List.of(typed));

        assertThat(controller.bulkFindingAction(jobId, r, user("op", "ROLE_OPERATOR")).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void accountantBulkOverride_approvesEachInOneStep_withItsOwnApplyCall() {
        ValidationFinding a = finding("A", ResolveStatus.OPEN);
        ValidationFinding b = finding("B", ResolveStatus.OPEN);
        when(findingRepository.findMatchingForUpdate(any(), eq(ResolveStatus.OPEN), any(), any(), any(), any()))
                .thenReturn(List.of(a, b));

        ResponseEntity<?> res = controller.bulkFindingAction(jobId, filterReq("ACKNOWLEDGE"), user("acct", "ROLE_ACCOUNTANT"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(a.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(b.getResolvedBy()).isEqualTo("acct");
        verify(overrideService).apply(eq(a), eq(orgId), eq("acct"), any());
        verify(overrideService).apply(eq(b), eq(orgId), eq("acct"), any());
        verify(overrideService, times(1)).activeMastersByName(orgId);
    }

    @Test
    void bulkOverride_byIds_targetsOnlyTheListedFindings() {
        ValidationFinding a = finding("A", ResolveStatus.OPEN);
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setFindingIds(List.of(a.getId()));
        when(findingRepository.findByIdsForUpdate(eq(jobId), any())).thenReturn(List.of(a));

        ResponseEntity<?> res = controller.bulkFindingAction(jobId, r, user("op", "ROLE_OPERATOR"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(findingRepository, never()).findMatchingForUpdate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkOverride_anyFailureAbortsWholeBatch_reportingEachFailedFinding() {
        ValidationFinding good = finding("Good", ResolveStatus.OPEN);
        ValidationFinding bad = finding("Bad", ResolveStatus.OPEN);
        ValidationFinding notOpen = finding("Pending", ResolveStatus.ACKNOWLEDGED);
        when(findingRepository.findByIdsForUpdate(eq(jobId), any())).thenReturn(List.of(good, bad, notOpen));
        lenient().doThrow(new FindingOverrideException(HttpStatus.BAD_REQUEST, "needs a value")).when(overrideService)
                .validate(eq(bad), any(), any());
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setFindingIds(List.of(good.getId(), bad.getId(), notOpen.getId()));

        assertThatThrownBy(() -> controller.bulkFindingAction(jobId, r, user("acct", "ROLE_ACCOUNTANT")))
                .isInstanceOfSatisfying(BulkFindingActionException.class, e -> {
                    assertThat(e.getFailures()).extracting(BulkFindingActionException.Failure::ledgerName)
                            .containsExactlyInAnyOrder("Bad", "Pending");
                    assertThat(e.getMessage()).contains("2 findings").contains("nothing was changed");
                });
        verify(findingRepository, never()).saveAll(any());
        assertThat(controller.handleBulkFailure(new BulkFindingActionException("m", List.of())).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void bulkOverride_unknownIdIsReportedAsFailure() {
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        UUID missing = UUID.randomUUID();
        ValidationFinding a = finding("A", ResolveStatus.OPEN);
        r.setFindingIds(List.of(a.getId(), missing));
        when(findingRepository.findByIdsForUpdate(eq(jobId), any())).thenReturn(List.of(a));

        assertThatThrownBy(() -> controller.bulkFindingAction(jobId, r, user("op", "ROLE_OPERATOR")))
                .isInstanceOfSatisfying(BulkFindingActionException.class,
                        e -> assertThat(e.getFailures()).extracting(BulkFindingActionException.Failure::findingId)
                                .containsExactly(missing));
    }

    @Test
    void accountantBulkApprove_approvesPendingSubmissions_keepingSubmitter() {
        ValidationFinding a = finding("A", ResolveStatus.ACKNOWLEDGED);
        a.setSubmittedBy("op");
        ValidationFinding b = finding("B", ResolveStatus.ACKNOWLEDGED);
        b.setSubmittedBy("op");
        when(findingRepository.findMatchingForUpdate(jobId, ResolveStatus.ACKNOWLEDGED, null, null, null,
                MismatchType.MISSING_IN_CONFIGURATION)).thenReturn(List.of(a, b));

        ResponseEntity<?> res = controller.bulkFindingAction(jobId, filterReq("APPROVE"), user("acct", "ROLE_ACCOUNTANT"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(a.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(a.getSubmittedBy()).isEqualTo("op");
        assertThat(a.getResolvedBy()).isEqualTo("acct");
        verify(overrideService, times(2)).apply(any(), eq(orgId), eq("acct"), any());
        verify(overrideService, times(1)).activeMastersByName(orgId);
    }

    @Test
    void bulkApprove_staleMasterOnOneFinding_failsWholeBatch() {
        ValidationFinding a = finding("A", ResolveStatus.ACKNOWLEDGED);
        ValidationFinding b = finding("B", ResolveStatus.ACKNOWLEDGED);
        when(findingRepository.findMatchingForUpdate(any(), eq(ResolveStatus.ACKNOWLEDGED), any(), any(), any(), any()))
                .thenReturn(List.of(a, b));
        lenient().doThrow(new FindingOverrideException(HttpStatus.CONFLICT, "Master changed since submission; reject and resubmit."))
                .when(overrideService).apply(eq(b), any(), anyString(), any());

        assertThatThrownBy(() -> controller.bulkFindingAction(jobId, filterReq("APPROVE"), user("acct", "ROLE_ACCOUNTANT")))
                .isInstanceOfSatisfying(BulkFindingActionException.class, e -> {
                    assertThat(e.getFailures()).hasSize(1);
                    assertThat(e.getFailures().get(0).ledgerName()).isEqualTo("B");
                    assertThat(e.getFailures().get(0).error()).contains("Master changed");
                });
        verify(findingRepository, never()).saveAll(any());
    }

    @Test
    void bulkApprove_byAnOperator_isForbidden() {
        ResponseEntity<?> res = controller.bulkFindingAction(jobId, filterReq("APPROVE"), user("op", "ROLE_OPERATOR"));

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        verify(findingRepository, never()).findMatchingForUpdate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkApprove_ofOpenFindingsById_isReportedNotApproved() {
        ValidationFinding open = finding("Open", ResolveStatus.OPEN);
        BulkFindingActionRequest r = new BulkFindingActionRequest();
        r.setAction("APPROVE");
        r.setFindingIds(List.of(open.getId()));
        when(findingRepository.findByIdsForUpdate(eq(jobId), any())).thenReturn(List.of(open));

        assertThatThrownBy(() -> controller.bulkFindingAction(jobId, r, user("acct", "ROLE_ACCOUNTANT")))
                .isInstanceOf(BulkFindingActionException.class);
        verify(overrideService, never()).apply(any(), any(), anyString(), any());
    }

    @Test
    void bulkAction_withNoIdsAndNoFilter_returns400_andUnknownActionReturns400() {
        UserDetailsImpl acct = user("acct", "ROLE_ACCOUNTANT");
        assertThat(controller.bulkFindingAction(jobId, new BulkFindingActionRequest(), acct).getStatusCode().value())
                .isEqualTo(400);
        assertThat(controller.bulkFindingAction(jobId, filterReq("REJECT"), acct).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void bulkAction_nothingMatches_returns400() {
        when(findingRepository.findMatchingForUpdate(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThat(controller.bulkFindingAction(jobId, filterReq("ACKNOWLEDGE"), user("op", "ROLE_OPERATOR"))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void bulkAction_onAnotherOrganizationsJob_returns404() {
        UserDetailsImpl other = new UserDetailsImpl(2L, "x", "x@t.com", "x", UUID.randomUUID(),
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT")));
        assertThat(controller.bulkFindingAction(jobId, filterReq("ACKNOWLEDGE"), other).getStatusCode().value())
                .isEqualTo(404);
    }
}
