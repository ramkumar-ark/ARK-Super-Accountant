package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.FinancialYearClose;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.arktech.superaccountant.workflow.repository.FinancialYearCloseRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.seam.ResponsibilityStartService;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.workflow.seam.UploadFreshnessGate;
import com.arktech.superaccountant.workflow.seam.WorkflowGate;
import com.arktech.superaccountant.workflow.service.compliance.CompliancePeriods;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowService {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    @Autowired
    private FilerProfileRepository filerProfileRepository;

    @Autowired
    private FinancialYearCloseRepository financialYearCloseRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private WorkflowGate workflowGate;

    @Autowired
    private MastersJobGate mastersJobGate;

    @Autowired
    private UploadFreshnessGate uploadFreshnessGate;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private ResponsibilityStartService responsibilityStartService;

    // ObjectProvider keeps this lazy so TdsApplicabilityService (which depends on
    // ResponsibilityStartSettingsService → WorkflowService) can be wired without a cycle.
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<
            com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService> tdsApplicabilityServiceProvider;

    private boolean isTdsType(WorkflowType type) {
        return type == WorkflowType.TDS_PAYMENT || type == WorkflowType.TDS_26Q;
    }

    /** FY 2025 renders as "FY 2025-26". */
    public static String fyLabel(int financialYear) {
        return "FY " + financialYear + "-" + String.format("%02d", (financialYear + 1) % 100);
    }

    @Autowired
    private com.arktech.superaccountant.workflow.seam.BaselineGate baselineGate;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private List<com.arktech.superaccountant.workflow.seam.FinalizePrecondition> finalizePreconditions
            = List.of();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private List<com.arktech.superaccountant.workflow.seam.PrepareHook> prepareHooks = List.of();

    private void firePrepareHooks(java.util.function.Consumer<com.arktech.superaccountant.workflow.seam.PrepareHook> action) {
        for (com.arktech.superaccountant.workflow.seam.PrepareHook hook : prepareHooks) {
            action.accept(hook);
        }
    }

    // ---------- lookups ----------

    public List<Workflow> list(UUID orgId, String type, Integer fy, String stage) {
        List<Workflow> all = workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId);
        return all.stream()
                .filter(w -> type == null || w.getType().name().equals(type))
                .filter(w -> fy == null || w.getFinancialYear() == fy)
                .filter(w -> stage == null || w.getStage().name().equals(stage))
                .filter(w -> !isTdsType(w.getType())
                        || w.getStage() == WorkflowStage.FINALIZED
                        || w.getStage() == WorkflowStage.FILED
                        || tdsApplicabilityServiceProvider.getObject()
                                .isApplicable(orgId, w.getFinancialYear()))
                .toList();
    }

    public Workflow get(UUID orgId, UUID id) {
        Workflow w = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowBadRequestException("Workflow not found"));
        if (!w.getOrganizationId().equals(orgId)) {
            throw new WorkflowBadRequestException("Workflow not found");
        }
        return w;
    }

    public List<WorkflowEvent> events(UUID orgId, UUID id) {
        get(orgId, id);
        return workflowEventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(id);
    }

    // ---------- filer profile ----------

    public FilerProfile profileInForce(UUID organizationId, LocalDate onOrBefore) {
        return filerProfileRepository
                .findFirstByOrganizationIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        organizationId, onOrBefore)
                .orElse(null);
    }

    public boolean isQuarterStart(LocalDate date, int fyStartMonth) {
        return PeriodCalendar.isQuarterStart(date, fyStartMonth);
    }

    // ---------- start ----------

    @Transactional
    public Workflow start(UUID organizationId, String actor, WorkflowType type, String periodKey) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new WorkflowBadRequestException("Organization not found"));
        int fyStartMonth = org.getFinancialYearStart();

        boolean keyIsQuarterly = periodKey != null && periodKey.contains("-Q");
        if (type == WorkflowType.TDS_PAYMENT && keyIsQuarterly) {
            throw new WorkflowBadRequestException("TDS_PAYMENT periods are monthly");
        }
        if (type == WorkflowType.TDS_26Q && !keyIsQuarterly) {
            throw new WorkflowBadRequestException("TDS_26Q periods are quarterly");
        }
        if (type == WorkflowType.IFF && keyIsQuarterly) {
            throw new WorkflowBadRequestException("IFF periods are monthly");
        }

        PeriodCalendar.PeriodRange range;
        try {
            range = keyIsQuarterly
                    ? PeriodCalendar.resolveQuarter(periodKey, fyStartMonth)
                    : PeriodCalendar.resolveMonthly(periodKey, fyStartMonth);
        } catch (java.time.DateTimeException e) {
            throw new WorkflowBadRequestException("Invalid period key: " + periodKey);
        }

        if (isTdsType(type) && !tdsApplicabilityServiceProvider.getObject()
                .isApplicableOn(organizationId, range.start())) {
            throw new WorkflowBadRequestException(
                    "TDS is not applicable for " + fyLabel(range.financialYear()));
        }

        if (type == WorkflowType.GSTR1 || type == WorkflowType.GSTR3B) {
            FilerProfile profile = profileInForce(organizationId, range.start());
            boolean expectedQuarterly = CompliancePeriods.isQuarterlyGrain(profile, type);
            if (expectedQuarterly != keyIsQuarterly) {
                throw new WorkflowBadRequestException(
                        "Period key does not match the filer profile in force for this period");
            }
        }

        if (type == WorkflowType.IFF) {
            FilerProfile profile = profileInForce(organizationId, range.start());
            if (!CompliancePeriods.isIffEligible(profile, range.start(), fyStartMonth)) {
                throw new WorkflowBadRequestException(
                        "IFF is only available for months 1 and 2 of a quarter to QRMP filers who opted in");
            }
        }

        LocalDate coverageStart = coverageService.coverageStart(organizationId);
        if (range.start().isBefore(coverageStart)) {
            throw new WorkflowBadRequestException(
                    "Period starts before coverage start " + coverageStart);
        }

        responsibilityStartService.responsibilityStart(organizationId, type).ifPresent(rs -> {
            if (range.start().isBefore(rs)) {
                throw new WorkflowBadRequestException(
                        "Period starts before responsibility start " + rs);
            }
        });

        Workflow existing = workflowRepository
                .findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                        organizationId, type, periodKey)
                .orElse(null);

        int revision = 0;
        UUID supersedes = null;
        if (existing != null) {
            if (existing.getStage() != WorkflowStage.FINALIZED && existing.getStage() != WorkflowStage.FILED) {
                throw new WorkflowConflictException(
                        "An active workflow already exists for this company, type and period");
            }
            revision = existing.getRevision() + 1;
            supersedes = existing.getId();
            existing.setActive(false);
            workflowRepository.save(existing);
            workflowEventRepository.save(event(existing.getId(), actor,
                    WorkflowEventKind.AMENDMENT_RECORDED,
                    "Superseded by revision " + revision, null));
        }

        Workflow w = new Workflow();
        w.setOrganizationId(organizationId);
        w.setType(type);
        w.setPeriodStart(range.start());
        w.setPeriodEnd(range.end());
        w.setPeriodKey(periodKey);
        w.setFinancialYear(range.financialYear());
        w.setRevision(revision);
        w.setSupersedesWorkflowId(supersedes);
        w.setStage(WorkflowStage.STARTED);
        w.setActive(true);
        w.setCreatedBy(actor);
        workflowRepository.save(w);

        List<DependencyStatus> deps = evaluateDependencies(w);
        String advisory = deps.stream().filter(d -> !d.satisfied())
                .map(d -> d.type() + " " + d.periodKey())
                .reduce((a, b) -> a + ", " + b)
                .map(unmet -> "Advisory: not yet finalized: " + unmet)
                .orElse(null);

        workflowEventRepository.save(event(w.getId(), actor, WorkflowEventKind.STARTED,
                advisory, payloadOf("revision", revision)));
        return w;
    }

    // ---------- advance / finalize / unfinalize / filing ----------

    @Transactional
    public Workflow advanceToPrepare(UUID organizationId, String actor, UUID id) {
        Workflow w = get(organizationId, id);
        if (w.getStage() != WorkflowStage.FINDINGS_REVIEW) {
            throw new WorkflowConflictException(
                    "Workflow is in " + w.getStage() + " stage; only a workflow in FINDINGS_REVIEW can move to PREPARE");
        }
        requireZeroBlocking(w);
        if (!baselineGate.isConfirmed(organizationId)) {
            throw new WorkflowConflictException("Baseline must be confirmed before Prepare");
        }
        UUID mastersJobId = w.getMastersJobId();
        if (mastersJobId != null) {
            UUID latest = mastersJobGate.latestCompletedMastersJob(organizationId).orElse(null);
            if (!mastersJobId.equals(latest)) {
                throw new WorkflowConflictException(
                        "Masters job is not the latest completed masters job");
            }
        }
        w.setStage(WorkflowStage.PREPARE);
        workflowRepository.save(w);
        firePrepareHooks(hook -> {
            if (hook.appliesTo(w)) {
                hook.onEnterPrepare(w);
            }
        });
        workflowEventRepository.save(event(id, actor, WorkflowEventKind.ADVANCED, "PREPARE", null));
        return w;
    }

    @Transactional
    public Workflow finalize(UUID organizationId, String actor, UUID id) {
        Workflow w = get(organizationId, id);
        if (w.getStage() != WorkflowStage.PREPARE) {
            throw new WorkflowConflictException("Only a workflow in PREPARE can be finalized");
        }
        requireZeroBlocking(w);

        List<DependencyStatus> unmet = evaluateDependencies(w).stream()
                .filter(d -> !d.satisfied())
                .toList();
        if (!unmet.isEmpty()) {
            throw new WorkflowConflictException("Dependencies not finalized: "
                    + unmet.stream().map(d -> d.type() + " " + d.periodKey())
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }

        List<String> unmetPreconditions = new ArrayList<>();
        for (com.arktech.superaccountant.workflow.seam.FinalizePrecondition precondition
                : finalizePreconditions) {
            if (precondition.appliesTo(w)) {
                unmetPreconditions.addAll(precondition.unmet(w));
            }
        }
        if (!unmetPreconditions.isEmpty()) {
            throw new WorkflowConflictException("Finalize preconditions not met: "
                    + String.join("; ", unmetPreconditions));
        }

        if (w.getUploadJobId() != null
                && !uploadFreshnessGate.isStillLatestForPeriod(organizationId, w.getUploadJobId(), w.getPeriodStart(), w.getPeriodEnd())) {
            throw new WorkflowConflictException(
                    "The bound upload is no longer the latest upload for this period");
        }

        w.setStage(WorkflowStage.FINALIZED);
        w.setFinalizedAt(Instant.now());
        w.setFinalizedBy(actor);
        w.setFrozenThrough(w.getPeriodEnd());
        workflowRepository.save(w);
        snapshotService.freeze(w.getId(), organizationId, actor);
        firePrepareHooks(hook -> {
            if (hook.appliesTo(w)) {
                hook.onFinalize(w, actor);
            }
        });
        workflowEventRepository.save(event(id, actor, WorkflowEventKind.FINALIZED, null, null));
        return w;
    }

    @Transactional
    public Workflow unfinalize(UUID organizationId, String actor, UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new WorkflowBadRequestException("A reason is required to un-finalize");
        }
        Workflow w = get(organizationId, id);
        if (w.getStage() != WorkflowStage.FINALIZED) {
            throw new WorkflowConflictException("Only a FINALIZED workflow can be un-finalized");
        }

        List<Workflow> laterFinalized = workflowRepository
                .findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(o -> o.isActive() && o.getType() == w.getType()
                        && !o.getId().equals(w.getId())
                        && o.getPeriodStart().isAfter(w.getPeriodStart())
                        && (o.getStage() == WorkflowStage.FINALIZED || o.getStage() == WorkflowStage.FILED))
                .toList();
        if (!laterFinalized.isEmpty()) {
            throw new WorkflowConflictException(
                    "Only the latest finalized workflow of its type can be un-finalized");
        }

        List<Workflow> finalizedDependents = workflowRepository
                .findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(o -> o.isActive() && (o.getStage() == WorkflowStage.FINALIZED
                        || o.getStage() == WorkflowStage.FILED))
                .filter(o -> dependsOn(workflowRepository.findById(o.getId()).orElse(o), w))
                .toList();
        if (!finalizedDependents.isEmpty()) {
            throw new WorkflowConflictException(
                    "A finalized workflow depends on this one");
        }

        w.setStage(WorkflowStage.PREPARE);
        w.setFinalizedAt(null);
        w.setFinalizedBy(null);
        w.setFrozenThrough(null);
        workflowRepository.save(w);
        snapshotService.release(w.getId(), organizationId, actor);
        firePrepareHooks(hook -> {
            if (hook.appliesTo(w)) {
                hook.onUnfinalize(w);
            }
        });
        workflowEventRepository.save(event(id, actor, WorkflowEventKind.UNFINALIZED, reason, null));
        return w;
    }

    @Transactional
    public Workflow recordFiling(UUID organizationId, String actor, UUID id,
                                 String filingRef, LocalDate filingRefDate) {
        return recordFiling(organizationId, actor, id, filingRef, filingRefDate,
                "PORTAL_FILE");
    }

    @Transactional
    public Workflow recordFiling(UUID organizationId, String actor, UUID id,
                                 String filingRef, LocalDate filingRefDate,
                                 String filingChannel) {
        if (filingRef == null || filingRef.isBlank() || filingRefDate == null) {
            throw new WorkflowBadRequestException("Filing reference and date are required");
        }
        String channel = filingChannel == null || filingChannel.isBlank()
                ? "PORTAL_FILE" : filingChannel;
        try {
            com.arktech.superaccountant.gstr1.channel.ChannelKind.valueOf(channel);
        } catch (IllegalArgumentException e) {
            throw new WorkflowBadRequestException("Unknown filing channel: " + channel);
        }
        Workflow w = get(organizationId, id);
        if (w.getStage() != WorkflowStage.FINALIZED) {
            throw new WorkflowConflictException("Only a FINALIZED workflow can be marked as filed");
        }
        w.setStage(WorkflowStage.FILED);
        w.setFilingRef(filingRef);
        w.setFilingRefDate(filingRefDate);
        w.setFilingChannel(channel);
        w.setFiledAt(Instant.now());
        w.setFiledBy(actor);
        workflowRepository.save(w);
        workflowEventRepository.save(event(id, actor, WorkflowEventKind.FILED,
                null, payloadOf("filingRef", filingRef)));
        return w;
    }

    @Transactional
    public void recordPortalUpload(UUID organizationId, String actor, UUID id,
                                   String fileHash, String note) {
        Workflow w = get(organizationId, id);
        if (w.getStage() != WorkflowStage.FINALIZED) {
            throw new WorkflowConflictException(
                    "Portal upload can be recorded only on a FINALIZED workflow");
        }
        if (fileHash != null && !fileHash.isBlank() && !fileHash.matches("[0-9a-fA-F]{64}")) {
            throw new WorkflowBadRequestException("fileHash must be a 64-character hex SHA-256");
        }
        String sha256 = fileHash == null || fileHash.isBlank()
                ? PortalDownloadHashes.latestSha256(workflowEventRepository, w.getId())
                : fileHash;
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sha256", sha256);
        if (note != null && !note.isBlank()) {
            payload.put("note", note);
        }
        String json;
        try {
            json = PortalDownloadHashes.mapper().writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        workflowEventRepository.save(event(w.getId(), actor,
                WorkflowEventKind.PORTAL_UPLOADED, null, json));
    }

    @Transactional
    public void recordPortalFileDownload(UUID organizationId, String actor, UUID id,
                                         String sha256, long bytes, String writerVersion,
                                         int parts) {
        Workflow w = get(organizationId, id);
        workflowEventRepository.save(event(w.getId(), actor,
                WorkflowEventKind.PORTAL_FILE_DOWNLOADED, null, portalDownloadPayload(
                        sha256, bytes, writerVersion, parts)));
    }

    private String portalDownloadPayload(String sha256, long bytes, String writerVersion,
                                         int parts) {
        return "{\"sha256\":\"" + sha256 + "\",\"bytes\":" + bytes
                + ",\"writerVersion\":\"" + writerVersion.replace("\"", "\\\"")
                + "\",\"parts\":" + parts + "}";
    }

    // ---------- dependencies ----------

    public record DependencyStatus(String type, String periodKey, boolean satisfied) {
    }

    public List<DependencyStatus> evaluateDependencies(Workflow w) {
        if (w.getType() == WorkflowType.IFF) {
            return List.of();
        }
        UUID orgId = w.getOrganizationId();
        Organization org = organizationRepository.findById(orgId).orElse(null);
        int fyStartMonth = org == null ? 4 : org.getFinancialYearStart();
        Set<Integer> closedFys = closedFinancialYears(orgId);

        List<DependencyStatus> deps = new ArrayList<>();

        if (w.getType() == WorkflowType.GSTR3B) {
            deps.add(new DependencyStatus(WorkflowType.GSTR1.name(), w.getPeriodKey(),
                    isFinalizedFor(orgId, WorkflowType.GSTR1, w.getPeriodKey())));
        }

        if (w.getType() == WorkflowType.TDS_26Q) {
            for (String month : PeriodCalendar.quarterMonths(w.getPeriodKey(), fyStartMonth)) {
                deps.add(new DependencyStatus(WorkflowType.TDS_PAYMENT.name(), month,
                        isFinalizedFor(orgId, WorkflowType.TDS_PAYMENT, month)));
            }
        }

        String previousKey = previousPeriodKey(w, fyStartMonth);
        if (previousKey != null) {
            boolean prevFyClosed = financialYearOfKey(w, previousKey, fyStartMonth) != null
                    && closedFys.contains(financialYearOfKey(w, previousKey, fyStartMonth));
            deps.add(new DependencyStatus(w.getType().name(), previousKey,
                    prevFyClosed || isFinalizedFor(orgId, w.getType(), previousKey)));
        }

        return deps;
    }

    private Set<Integer> closedFinancialYears(UUID orgId) {
        Set<Integer> closed = new HashSet<>();
        for (FinancialYearClose c : financialYearCloseRepository
                .findAllByOrganizationIdOrderByFinancialYearAsc(orgId)) {
            closed.add(c.getFinancialYear());
        }
        return closed;
    }

    private String previousPeriodKey(Workflow w, int fyStartMonth) {
        boolean quarterly = isQuarterlyPeriodKey(w.getPeriodKey());
        String prev = quarterly
                ? PeriodCalendar.previousQuarterKey(w.getPeriodKey())
                : PeriodCalendar.previousMonthlyKey(w.getPeriodKey());
        LocalDate prevStart = quarterly
                ? PeriodCalendar.resolveQuarter(prev, fyStartMonth).start()
                : PeriodCalendar.resolveMonthly(prev, fyStartMonth).start();
        LocalDate coverageStart = coverageService.coverageStart(w.getOrganizationId());
        if (prevStart.isBefore(coverageStart)) {
            return null;
        }
        return prev;
    }

    private Integer financialYearOfKey(Workflow w, String key, int fyStartMonth) {
        try {
            PeriodCalendar.PeriodRange range = isQuarterlyPeriodKey(key)
                    ? PeriodCalendar.resolveQuarter(key, fyStartMonth)
                    : PeriodCalendar.resolveMonthly(key, fyStartMonth);
            return range.financialYear();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isQuarterlyPeriodKey(String key) {
        return key != null && key.contains("-Q");
    }

    private boolean isFinalizedFor(UUID orgId, WorkflowType type, String periodKey) {
        return workflowRepository.existsFinalized(orgId, type, periodKey);
    }

    private boolean dependsOn(Workflow dependent, Workflow candidate) {
        List<DependencyStatus> deps = evaluateDependencies(dependent);
        return deps.stream().anyMatch(d ->
                d.type().equals(candidate.getType().name())
                        && d.periodKey().equals(candidate.getPeriodKey()));
    }

    private void requireZeroBlocking(Workflow w) {
        int blocking = workflowGate.blockingFindingCount(w.getId());
        if (blocking > 0) {
            throw new WorkflowConflictException(
                    blocking + " blocking findings must be resolved first");
        }
    }

    public int blockingFindingCount(UUID workflowId) {
        return workflowGate.blockingFindingCount(workflowId);
    }

    // ---------- events ----------

    private WorkflowEvent event(UUID workflowId, String actor, WorkflowEventKind kind,
                                String reason, String payload) {
        WorkflowEvent e = new WorkflowEvent();
        e.setWorkflowId(workflowId);
        e.setActor(actor);
        e.setKind(kind);
        e.setReason(reason);
        e.setPayload(payload);
        return e;
    }

    private String payloadOf(String key, Object value) {
        return "{\"" + key + "\":" + (value instanceof Number || value instanceof Boolean
                ? String.valueOf(value)
                : "\"" + String.valueOf(value).replace("\"", "\\\"") + "\"") + "}";
    }
}
