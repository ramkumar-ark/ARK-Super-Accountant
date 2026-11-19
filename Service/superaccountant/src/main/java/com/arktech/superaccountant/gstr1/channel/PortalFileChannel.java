package com.arktech.superaccountant.gstr1.channel;

import com.arktech.superaccountant.gstr1.json.WriterOptions;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.service.PortalDownloadHashes;
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
        String sha256 = PortalDownloadHashes.latestSha256(
                workflowEventRepository, preparedReturn.getWorkflowId());
        return new ChannelResult(ChannelResult.Status.MANUAL_UPLOAD_REQUIRED, sha256,
                "Upload the downloaded portal file on the GST portal manually");
    }
}
