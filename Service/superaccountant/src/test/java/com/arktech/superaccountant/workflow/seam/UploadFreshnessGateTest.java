package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadFreshnessGateTest {

    @Mock DayBookUploadJobRepository dayBookUploadJobRepository;

    @InjectMocks
    UploadFreshnessGate gate;

    @Test
    void isStillLatest_trueOnlyForLatestCompleted() {
        UUID orgId = UUID.randomUUID();
        UUID latestId = UUID.randomUUID();
        DayBookUploadJob latest = new DayBookUploadJob();
        latest.setId(latestId);
        when(dayBookUploadJobRepository.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
                orgId, UploadJobStatus.COMPLETED)).thenReturn(Optional.of(latest));

        assertThat(gate.isStillLatest(orgId, latestId)).isTrue();
        assertThat(gate.isStillLatest(orgId, UUID.randomUUID())).isFalse();
        assertThat(gate.isStillLatest(orgId, null)).isFalse();
    }

    @Test
    void isStillLatest_falseWhenNoCompletedJob() {
        when(dayBookUploadJobRepository.findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
                any(), eq(UploadJobStatus.COMPLETED))).thenReturn(Optional.empty());

        assertThat(gate.isStillLatest(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }
}
