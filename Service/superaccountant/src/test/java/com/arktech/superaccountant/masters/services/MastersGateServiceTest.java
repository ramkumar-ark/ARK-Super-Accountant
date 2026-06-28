package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.GateResult;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MastersGateServiceTest {

    @Mock
    UploadJobRepository uploadJobRepository;

    @Mock
    ValidationFindingRepository findingRepository;

    @InjectMocks
    MastersGateService gateService;

    private static final List<UploadJobStatus> COMPLETED_STATUSES =
            List.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES);

    @Test
    void checkGate_whenNoCompletedUploadJob_returnsGatedWithZeroCount() {
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), eq(COMPLETED_STATUSES))).thenReturn(Optional.empty());

        GateResult result = gateService.checkGate(UUID.randomUUID());

        assertThat(result.gated()).isTrue();
        assertThat(result.unresolvedCount()).isEqualTo(0);
    }

    @Test
    void checkGate_whenHighOpenFindingsExist_returnsGated() {
        UploadJob job = new UploadJob();
        job.setId(UUID.randomUUID());
        job.setTotalLedgersParsed(5);
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), eq(COMPLETED_STATUSES))).thenReturn(Optional.of(job));
        when(findingRepository.countHighSeverityUnresolved(job.getId())).thenReturn(3L);

        GateResult result = gateService.checkGate(UUID.randomUUID());

        assertThat(result.gated()).isTrue();
        assertThat(result.unresolvedCount()).isEqualTo(3);
    }

    @Test
    void checkGate_whenAllFindingsResolved_returnsOpen() {
        UploadJob job = new UploadJob();
        job.setId(UUID.randomUUID());
        job.setTotalLedgersParsed(5);
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), eq(COMPLETED_STATUSES))).thenReturn(Optional.of(job));
        when(findingRepository.countHighSeverityUnresolved(job.getId())).thenReturn(0L);

        GateResult result = gateService.checkGate(UUID.randomUUID());

        assertThat(result.gated()).isFalse();
        assertThat(result.unresolvedCount()).isEqualTo(0);
    }

    @Test
    void checkGate_whenZeroLedgersParsed_returnsGated() {
        UploadJob job = new UploadJob();
        job.setId(UUID.randomUUID());
        job.setTotalLedgersParsed(0);
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), eq(COMPLETED_STATUSES))).thenReturn(Optional.of(job));

        GateResult result = gateService.checkGate(UUID.randomUUID());

        assertThat(result.gated()).isTrue();
        assertThat(result.unresolvedCount()).isEqualTo(0);
    }

    @Test
    void checkGate_whenTotalLedgersParsedNull_returnsGated() {
        UploadJob job = new UploadJob();
        job.setId(UUID.randomUUID());
        job.setTotalLedgersParsed(null);
        when(uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                any(UUID.class), eq(COMPLETED_STATUSES))).thenReturn(Optional.of(job));

        GateResult result = gateService.checkGate(UUID.randomUUID());

        assertThat(result.gated()).isTrue();
        assertThat(result.unresolvedCount()).isEqualTo(0);
    }
}
