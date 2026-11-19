package com.arktech.superaccountant.gstr1.channel;

import com.arktech.superaccountant.gstr1.json.WriterOptions;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Portal-file channel (plan Q10): no direct submit. The accountant uploads the
 * downloaded portal file manually, so the channel reports MANUAL_UPLOAD_REQUIRED
 * with the hash of the latest downloaded portal file (ADR-0003: the file itself
 * is regenerated, never stored, so the hash is the reference).
 */
@Component
public class PortalFileChannel implements Gstr1FilingChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private final WorkflowEventRepository workflowEventRepository;

    @Autowired
    public PortalFileChannel(WorkflowEventRepository workflowEventRepository) {
        this.workflowEventRepository = workflowEventRepository;
    }

    @Override
    public ChannelKind kind() {
        return ChannelKind.PORTAL_FILE;
    }

    @Override
    public boolean supportsDirectSubmit() {
        return false;
    }

    @Override
    public ChannelResult submit(PreparedReturn preparedReturn, WriterOptions options) {
        String sha256 = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(preparedReturn.getWorkflowId())
                .stream()
                .filter(e -> e.getKind() == WorkflowEventKind.PORTAL_FILE_DOWNLOADED)
                .reduce((first, second) -> second)
                .map(this::downloadSha256)
                .orElse(null);
        return new ChannelResult(ChannelResult.Status.MANUAL_UPLOAD_REQUIRED, sha256,
                "Upload the downloaded portal file on the GST portal manually");
    }

    private String downloadSha256(WorkflowEvent event) {
        try {
            JsonNode payload = MAPPER.readTree(event.getPayload());
            return payload.path("sha256").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
