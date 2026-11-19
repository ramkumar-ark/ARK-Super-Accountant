package com.arktech.superaccountant.gstr1.channel;

import com.arktech.superaccountant.gstr1.json.WriterOptions;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalFileChannelTest {

    private static final class StubDirectChannel implements Gstr1FilingChannel {
        @Override
        public ChannelKind kind() {
            return ChannelKind.GSP;
        }

        @Override
        public boolean supportsDirectSubmit() {
            return true;
        }

        @Override
        public ChannelResult submit(PreparedReturn preparedReturn, WriterOptions options) {
            return new ChannelResult(ChannelResult.Status.SUBMITTED, "GSP-REF-1", "done");
        }
    }

    private static WorkflowEvent event(WorkflowEventKind kind, String payload, Instant at) {
        WorkflowEvent e = new WorkflowEvent();
        e.setWorkflowId(UUID.randomUUID());
        e.setKind(kind);
        e.setPayload(payload);
        e.setOccurredAt(at);
        return e;
    }

    private static PreparedReturn preparedReturn(UUID workflowId) {
        PreparedReturn p = new PreparedReturn();
        p.setWorkflowId(workflowId);
        return p;
    }

    private static WorkflowEventRepository eventsRepo(UUID workflowId, List<WorkflowEvent> events) {
        WorkflowEventRepository repo = mock(WorkflowEventRepository.class);
        when(repo.findAllByWorkflowIdOrderByOccurredAtAsc(workflowId)).thenReturn(events);
        return repo;
    }

    @Test
    void portalFileChannelIsNotDirectSubmit() {
        PortalFileChannel channel = new PortalFileChannel(
                eventsRepo(UUID.randomUUID(), List.of()));

        assertThat(channel.kind()).isEqualTo(ChannelKind.PORTAL_FILE);
        assertThat(channel.supportsDirectSubmit()).isFalse();
    }

    @Test
    void submitReturnsManualUploadRequiredWithFileHash() {
        UUID workflowId = UUID.randomUUID();
        String sha256 = "a".repeat(64);
        WorkflowEventRepository repo = eventsRepo(workflowId, List.of(
                event(WorkflowEventKind.PORTAL_FILE_DOWNLOADED,
                        "{\"sha256\":\"" + sha256 + "\",\"bytes\":10}", Instant.now())));
        PortalFileChannel channel = new PortalFileChannel(repo);

        ChannelResult result = channel.submit(preparedReturn(workflowId), WriterOptions.defaults());

        assertThat(result.status()).isEqualTo(ChannelResult.Status.MANUAL_UPLOAD_REQUIRED);
        assertThat(result.reference()).isEqualTo(sha256);
    }

    @Test
    void submitUsesLatestDownloadEventWhenSeveralExist() {
        UUID workflowId = UUID.randomUUID();
        WorkflowEventRepository repo = eventsRepo(workflowId, List.of(
                event(WorkflowEventKind.PORTAL_FILE_DOWNLOADED,
                        "{\"sha256\":\"" + "1".repeat(64) + "\",\"bytes\":10}",
                        Instant.now().minusSeconds(60)),
                event(WorkflowEventKind.PORTAL_FILE_DOWNLOADED,
                        "{\"sha256\":\"" + "2".repeat(64) + "\",\"bytes\":20}", Instant.now())));
        PortalFileChannel channel = new PortalFileChannel(repo);

        ChannelResult result = channel.submit(preparedReturn(workflowId), WriterOptions.defaults());

        assertThat(result.reference()).isEqualTo("2".repeat(64));
    }

    @Test
    void submitWithoutDownloadEventReturnsNullReference() {
        PortalFileChannel channel = new PortalFileChannel(
                eventsRepo(UUID.randomUUID(), List.of()));

        ChannelResult result = channel.submit(preparedReturn(UUID.randomUUID()),
                WriterOptions.defaults());

        assertThat(result.status()).isEqualTo(ChannelResult.Status.MANUAL_UPLOAD_REQUIRED);
        assertThat(result.reference()).isNull();
    }

    @Test
    void stubDirectChannelCanBeRegisteredWithoutWriter() {
        Gstr1FilingChannel channel = new StubDirectChannel();

        ChannelResult result = channel.submit(preparedReturn(UUID.randomUUID()),
                WriterOptions.defaults());

        assertThat(channel.kind()).isEqualTo(ChannelKind.GSP);
        assertThat(channel.supportsDirectSubmit()).isTrue();
        assertThat(result.status()).isEqualTo(ChannelResult.Status.SUBMITTED);
        assertThat(result.reference()).isEqualTo("GSP-REF-1");
    }
}
