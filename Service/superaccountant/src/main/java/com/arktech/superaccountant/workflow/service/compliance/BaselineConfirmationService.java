package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.response.BaselineStatusResponse;
import com.arktech.superaccountant.workflow.repository.BaselineImportRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.BaselineGate;
import com.arktech.superaccountant.workflow.seam.CompanyBlockerSource;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.workflow.service.UploadNotFoundException;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BaselineConfirmationService {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private BaselineImportRepository baselineImportRepository;

    @Autowired
    private BaselineGate baselineGate;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired(required = false)
    private List<CompanyBlockerSource> blockerSources;

    private List<String> confirmBlockers(UUID organizationId) {
        if (blockerSources == null) {
            return List.of();
        }
        return blockerSources.stream()
                .flatMap(s -> s.baselineConfirmBlockers(organizationId).stream())
                .toList();
    }

    public BaselineStatusResponse status(UUID organizationId) {
        List<Workflow> unconfirmed = unconfirmedRows(organizationId);
        LocalDate rangeStart = unconfirmed.stream()
                .map(Workflow::getPeriodStart).min(Comparator.naturalOrder()).orElse(null);
        LocalDate rangeEnd = unconfirmed.stream()
                .map(Workflow::getPeriodEnd).max(Comparator.naturalOrder()).orElse(null);
        Map<WorkflowType, List<Workflow>> unconfirmedByType = groupByType(unconfirmed);
        Map<WorkflowType, Long> confirmedByType = workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(organizationId, WorkflowStage.BASELINE)
                .stream()
                .filter(w -> w.getBaselineConfirmedAt() != null)
                .collect(java.util.stream.Collectors.groupingBy(Workflow::getType,
                        java.util.stream.Collectors.counting()));
        List<BaselineStatusResponse.TypeStatus> types = new ArrayList<>();
        for (WorkflowType type : WorkflowType.values()) {
            int unconfirmedRows = unconfirmedByType.getOrDefault(type, List.of()).size();
            int confirmedRows = confirmedByType.getOrDefault(type, 0L).intValue();
            LocalDate latestEnd = unconfirmedByType.getOrDefault(type, List.of()).stream()
                    .map(Workflow::getPeriodEnd).max(Comparator.naturalOrder()).orElse(null);
            types.add(new BaselineStatusResponse.TypeStatus(type.name(), unconfirmedRows, confirmedRows, latestEnd));
        }
        long imports = baselineImportRepository.countByWorkflowIdIn(
                unconfirmed.stream().map(Workflow::getId).toList());
        return new BaselineStatusResponse(
                baselineGate.isConfirmed(organizationId),
                rangeStart,
                rangeEnd,
                types,
                confirmBlockers(organizationId),
                imports > 0 ? "available" : "not available",
                null);
    }

    @Transactional
    public BaselineStatusResponse confirm(UUID organizationId, String actor, UUID uploadJobId) {
        List<Workflow> unconfirmed = unconfirmedRows(organizationId);
        if (unconfirmed.isEmpty()) {
            throw new WorkflowConflictException("No unconfirmed baseline periods");
        }
        List<String> blockers = confirmBlockers(organizationId);
        if (!blockers.isEmpty()) {
            throw new WorkflowConflictException("Baseline cannot be confirmed: " + String.join("; ", blockers));
        }
        DayBookUploadJob upload;
        if (uploadJobId != null) {
            upload = dayBookUploadJobRepository.findById(uploadJobId)
                    .filter(u -> u.getOrganizationId().equals(organizationId))
                    .orElseThrow(UploadNotFoundException::new);
            if (upload.getStatus() != UploadJobStatus.COMPLETED) {
                throw new WorkflowConflictException("Upload is not completed");
            }
        } else {
            upload = dayBookUploadJobRepository
                    .findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, UploadJobStatus.COMPLETED)
                    .orElseThrow(() -> new WorkflowConflictException(
                            "Upload a day book covering the baseline range first"));
        }
        LocalDate rangeStart = unconfirmed.stream()
                .map(Workflow::getPeriodStart).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate rangeEnd = unconfirmed.stream()
                .map(Workflow::getPeriodEnd).max(Comparator.naturalOrder()).orElseThrow();
        if (upload.getCoverageFrom() == null || upload.getCoverageTo() == null
                || upload.getCoverageFrom().isAfter(rangeStart)
                || upload.getCoverageTo().isBefore(rangeEnd)) {
            throw new WorkflowConflictException(
                    "Upload does not cover the baseline range " + rangeStart + ".." + rangeEnd);
        }

        Map<WorkflowType, Workflow> latestEnding = new HashMap<>();
        Instant now = Instant.now();
        for (Workflow w : unconfirmed) {
            w.setUploadJobId(upload.getId());
            w.setBaselineConfirmedAt(now);
            w.setBaselineConfirmedBy(actor);
            w.setFrozenThrough(w.getPeriodEnd());
            workflowRepository.save(w);
            workflowEventRepository.save(event(w, actor,
                    "{\"uploadJobId\":\"" + upload.getId() + "\"}"));
            latestEnding.merge(w.getType(), w,
                    (a, b) -> b.getPeriodEnd().isAfter(a.getPeriodEnd()) ? b : a);
        }
        for (Workflow row : latestEnding.values()) {
            snapshotService.freeze(row.getId(), organizationId, actor);
        }
        return status(organizationId);
    }

    private List<Workflow> unconfirmedRows(UUID organizationId) {
        return workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(organizationId, WorkflowStage.BASELINE)
                .stream()
                .filter(w -> w.getBaselineConfirmedAt() == null)
                .toList();
    }

    private Map<WorkflowType, List<Workflow>> groupByType(List<Workflow> rows) {
        Map<WorkflowType, List<Workflow>> byType = new HashMap<>();
        for (Workflow w : rows) {
            byType.computeIfAbsent(w.getType(), t -> new ArrayList<>()).add(w);
        }
        return byType;
    }

    private WorkflowEvent event(Workflow w, String actor, String payload) {
        WorkflowEvent e = new WorkflowEvent();
        e.setWorkflowId(w.getId());
        e.setActor(actor);
        e.setKind(WorkflowEventKind.BASELINE_CONFIRMED);
        e.setPayload(payload);
        return e;
    }
}
