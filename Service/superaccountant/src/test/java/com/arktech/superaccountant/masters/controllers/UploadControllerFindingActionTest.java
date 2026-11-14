package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.payload.request.FindingActionRequest;
import com.arktech.superaccountant.masters.payload.response.FindingResponse;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideException;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.review.FindingReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Pure-logic tests of the two-stage finding action state machine (no Spring context, no Docker).
 * The transactional rollback behaviour is covered in FindingOverrideWorkflowIT.
 */
@ExtendWith(MockitoExtension.class)
class UploadControllerFindingActionTest {

    @Mock
    UploadJobRepository uploadJobRepository;
    @Mock
    ValidationFindingRepository findingRepository;
    @Mock
    FindingOverrideMasterService overrideService;
    @Spy
    FindingReviewService findingReviewService = new FindingReviewService();

    @InjectMocks
    UploadController controller;

    private final UUID orgId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private ValidationFinding finding;

    @BeforeEach
    void setUp() {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        lenient().when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        finding = new ValidationFinding();
        finding.setId(UUID.randomUUID());
        finding.setUploadJobId(jobId);
        finding.setRuleCode("GSTIN_PRESENCE");
        lenient().when(findingRepository.findByIdForUpdate(finding.getId())).thenReturn(Optional.of(finding));
    }

    private UserDetailsImpl user(String name, String role) {
        return new UserDetailsImpl(1L, name, name + "@t.com", "x", orgId, List.of(new SimpleGrantedAuthority(role)));
    }

    private FindingActionRequest req(String action, String note, String value) {
        FindingActionRequest r = new FindingActionRequest();
        r.setAction(action);
        r.setNote(note);
        r.setValue(value);
        return r;
    }

    private ResponseEntity<?> act(UserDetailsImpl u, String action, String note, String value) {
        return controller.findingAction(jobId, finding.getId(), req(action, note, value), u);
    }

    @Test
    void operatorAcknowledge_storesProposal_staysPending_neverWritesMaster() {
        ResponseEntity<?> res = act(user("op", "ROLE_OPERATOR"), "ACKNOWLEDGE", "typo", "29ABCDE1234F1Z5");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(finding.getProposedValue()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(finding.getSubmittedBy()).isEqualTo("op");
        assertThat(finding.getResolveNote()).isEqualTo("typo");
        assertThat(finding.getResolvedBy()).isNull();
        verify(overrideService).validate(any(), any(), any(), any());
        verify(overrideService, never()).apply(any(), any(), anyString());
    }

    @Test
    void accountantAcknowledge_isOneStep_approvedWithSubmitterEqualReviewerEqualResolver() {
        ResponseEntity<?> res = act(user("acct", "ROLE_ACCOUNTANT"), "ACKNOWLEDGE", null, "29ABCDE1234F1Z5");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(finding.getSubmittedBy()).isEqualTo("acct");
        assertThat(finding.getReviewedBy()).isEqualTo("acct");
        assertThat(finding.getResolvedBy()).isEqualTo("acct");
        verify(overrideService).apply(finding, orgId, "acct");
    }

    @Test
    void accountantApprove_ofOperatorProposal_appliesAsApproverAndKeepsSubmitter() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        finding.setSubmittedBy("op");
        finding.setResolveNote("typo");

        ResponseEntity<?> res = act(user("acct", "ROLE_ACCOUNTANT"), "APPROVE", null, null);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(finding.getSubmittedBy()).isEqualTo("op");
        assertThat(finding.getResolveNote()).isEqualTo("typo");
        assertThat(finding.getResolvedBy()).isEqualTo("acct");
        assertThat(finding.getReviewedBy()).isEqualTo("acct");
        verify(overrideService).apply(finding, orgId, "acct");
    }

    @Test
    void approveFailure_propagates_findingStaysAcknowledged() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        doThrow(new FindingOverrideException(HttpStatus.CONFLICT, "stale")).when(overrideService)
                .apply(any(), any(), anyString());

        try {
            act(user("acct", "ROLE_ACCOUNTANT"), "APPROVE", null, null);
            throw new AssertionError("expected exception");
        } catch (FindingOverrideException e) {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(finding.getResolvedBy()).isNull();
        verify(findingRepository, never()).save(any());
    }

    @Test
    void exceptionHandler_mapsStatusAndMessage() {
        ResponseEntity<?> res = controller.handleFindingOverride(
                new FindingOverrideException(HttpStatus.CONFLICT, "stale"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().toString()).contains("stale");
    }

    @Test
    void operatorApproveAndReject_areForbidden() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        UserDetailsImpl op = user("op", "ROLE_OPERATOR");

        assertThat(act(op, "APPROVE", null, null).getStatusCode().value()).isEqualTo(403);
        assertThat(act(op, "REJECT", "no", null).getStatusCode().value()).isEqualTo(403);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        verify(overrideService, never()).apply(any(), any(), anyString());
    }

    @Test
    void auditorAndOwner_areForbiddenOnEveryAction() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        for (String role : List.of("ROLE_AUDITOR_CA", "ROLE_OWNER")) {
            for (String action : List.of("ACKNOWLEDGE", "APPROVE", "REJECT")) {
                assertThat(act(user("u", role), action, "n", "v").getStatusCode().value())
                        .as(role + " " + action).isEqualTo(403);
            }
        }
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        verify(overrideService, never()).apply(any(), any(), anyString());
    }

    @Test
    void reject_requiresNote() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        UserDetailsImpl acct = user("acct", "ROLE_ACCOUNTANT");

        assertThat(act(acct, "REJECT", null, null).getStatusCode().value()).isEqualTo(400);
        assertThat(act(acct, "REJECT", "  ", null).getStatusCode().value()).isEqualTo(400);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
    }

    @Test
    void reject_returnsToOpen_keepsProposal_recordsReview_noMasterWrite() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        finding.setProposedValue("BAD");
        finding.setSubmittedBy("op");
        finding.setResolveNote("my reason");

        ResponseEntity<?> res = act(user("acct", "ROLE_ACCOUNTANT"), "REJECT", "wrong GSTIN", null);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(finding.getProposedValue()).isEqualTo("BAD");
        assertThat(finding.getSubmittedBy()).isEqualTo("op");
        assertThat(finding.getResolveNote()).isEqualTo("my reason");
        assertThat(finding.getReviewedBy()).isEqualTo("acct");
        assertThat(finding.getReviewNote()).isEqualTo("wrong GSTIN");
        assertThat(finding.getResolvedBy()).isNull();
        assertThat(finding.getResolvedAt()).isNull();
        FindingResponse body = (FindingResponse) res.getBody();
        assertThat(body.getReviewNote()).isEqualTo("wrong GSTIN");
        assertThat(body.getProposedValue()).isEqualTo("BAD");
        verify(overrideService, never()).apply(any(), any(), anyString());
    }

    @Test
    void resubmitAfterReject_overwritesProposalAndClearsReview() {
        finding.setResolveStatus(ResolveStatus.OPEN);
        finding.setProposedValue("BAD");
        finding.setReviewedBy("acct");
        finding.setReviewNote("wrong GSTIN");

        act(user("op", "ROLE_OPERATOR"), "ACKNOWLEDGE", "fixed", "GOOD");

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(finding.getProposedValue()).isEqualTo("GOOD");
        assertThat(finding.getReviewedBy()).isNull();
        assertThat(finding.getReviewNote()).isNull();
    }

    @Test
    void approveOrRejectOfNonPendingFinding_returns400() {
        UserDetailsImpl acct = user("acct", "ROLE_ACCOUNTANT");
        for (ResolveStatus st : List.of(ResolveStatus.OPEN, ResolveStatus.APPROVED)) {
            finding.setResolveStatus(st);
            assertThat(act(acct, "APPROVE", null, null).getStatusCode().value()).as("approve " + st).isEqualTo(400);
            assertThat(act(acct, "REJECT", "n", null).getStatusCode().value()).as("reject " + st).isEqualTo(400);
        }
        verify(overrideService, never()).apply(any(), any(), anyString());
    }

    @Test
    void acknowledgeOfNonOpenFinding_returns400() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        assertThat(act(user("op", "ROLE_OPERATOR"), "ACKNOWLEDGE", null, "v").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unknownAction_returns400() {
        assertThat(act(user("acct", "ROLE_ACCOUNTANT"), "RESOLVE", null, null).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void findingOfAnotherOrganizationsJob_returns404() {
        UserDetailsImpl other = new UserDetailsImpl(2L, "x", "x@t.com", "x", UUID.randomUUID(),
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT")));
        assertThat(act(other, "APPROVE", null, null).getStatusCode().value()).isEqualTo(404);
    }
}
