package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentRowSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the GSTR-1 prepared-return row each follow-up of a workflow
 * produced, searched across the organization's GSTR1 prepared returns
 * (a follow-up produces its row in a LATER return, not its own).
 */
@Component
public class Gstr1AmendmentRowSource implements AmendmentRowSource {

    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private AmendmentFollowUpRepository followUpRepository;
    @Autowired
    private PreparedReturnRowRepository rowRepository;

    @Override
    public Map<UUID, RowRef> rowsForFollowUps(UUID workflowId) {
        if (workflowId == null) {
            return Map.of();
        }
        UUID organizationId = workflowRepository.findById(workflowId)
                .map(Workflow::getOrganizationId)
                .orElse(null);
        if (organizationId == null) {
            return Map.of();
        }
        Set<UUID> followUpIds = followUpRepository
                .findByWorkflowIdOrderByCreatedAt(workflowId).stream()
                .map(f -> f.getId())
                .collect(Collectors.toSet());
        if (followUpIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RowRef> result = new LinkedHashMap<>();
        for (PreparedReturnRow row : rowRepository
                .findByOrganizationIdAndFollowUpIdIn(organizationId, followUpIds)) {
            result.putIfAbsent(row.getFollowUpId(), new RowRef(
                    row.getTableCode(), row.getRowKey(), row.isLateReporting()));
        }
        return result;
    }
}
