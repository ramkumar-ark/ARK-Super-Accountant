package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public final class PortalDownloadHashes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PortalDownloadHashes() {
    }

    public static String latestSha256(WorkflowEventRepository events, UUID workflowId) {
        return events.findAllByWorkflowIdOrderByOccurredAtAsc(workflowId)
                .stream()
                .filter(e -> e.getKind() == WorkflowEventKind.PORTAL_FILE_DOWNLOADED)
                .reduce((first, second) -> second)
                .map(e -> {
                    try {
                        return MAPPER.readTree(e.getPayload()).path("sha256").asText(null);
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
