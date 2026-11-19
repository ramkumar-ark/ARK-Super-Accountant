package com.arktech.superaccountant.gstr1.controllers;

import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.json.Gstr1JsonWriter;
import com.arktech.superaccountant.gstr1.json.Gstr1WriterVersion;
import com.arktech.superaccountant.gstr1.json.PortalFilePart;
import com.arktech.superaccountant.gstr1.json.WriterOptions;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.payload.request.Gstr1FillInRequest;
import com.arktech.superaccountant.gstr1.payload.request.PortalUploadRequest;
import com.arktech.superaccountant.gstr1.payload.response.Gstr1TablesResponse;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.Gstr1FillInService;
import com.arktech.superaccountant.gstr1.services.Gstr1PrepareService;
import com.arktech.superaccountant.gstr1.validation.Gstr1Validator;
import com.arktech.superaccountant.gstr1.validation.ValidationResult;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * GSTR-1 Prepare API: read the prepared return's table rows (grouped by table code,
 * with row counts and totals) and the fill-in list, and let an Accountant write
 * fill-ins while the workflow is in PREPARE (409 otherwise; 403 for other roles).
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class Gstr1WorkflowController {

    private static final String CARRY_FORWARD_ACTOR = "CARRY-FORWARD";
    private static final List<String> TOTAL_KEYS =
            List.of("taxableValue", "igst", "cgst", "sgst", "cess");

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private Gstr1PrepareService prepareService;

    @Autowired
    private PreparedReturnRowRepository rowRepository;

    @Autowired
    private Gstr1FillInService fillInService;

    @Autowired
    private Gstr1FillInRepository fillInRepository;

    @Autowired
    private Gstr1Validator validator;

    @Autowired
    private Gstr1JsonWriter jsonWriter;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FilerProfileRepository filerProfileRepository;

    @GetMapping("/{id}/gstr1/tables")
    public ResponseEntity<?> tables(@PathVariable UUID id,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow workflow = workflowService.get(principal.getOrganizationId(), id);
            PreparedReturn prepared = prepareService.buildOrRebuild(workflow);
            return ResponseEntity.ok(toResponse(workflow, prepared));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/gstr1/validation")
    public ResponseEntity<?> validation(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow workflow = workflowService.get(principal.getOrganizationId(), id);
            PreparedReturn prepared = prepareService.buildOrRebuild(workflow);
            List<ValidationResult> results = validator.validate(
                    workflow, rowRepository.findByPreparedReturnId(prepared.getId()));
            return ResponseEntity.ok(Map.of(
                    "errors", results.stream()
                            .filter(r -> r.severity() == ValidationResult.Severity.ERROR).toList(),
                    "warnings", results.stream()
                            .filter(r -> r.severity() == ValidationResult.Severity.WARNING).toList()));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ACCOUNTANT')")
    @PutMapping("/{id}/gstr1/fill-ins")
    public ResponseEntity<?> putFillIn(@PathVariable UUID id,
                                       @Valid @RequestBody Gstr1FillInRequest request,
                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow workflow = workflowService.get(principal.getOrganizationId(), id);
            Gstr1FillInScope scope;
            Gstr1FillInAttribute attribute;
            try {
                scope = Gstr1FillInScope.valueOf(request.scope());
                attribute = Gstr1FillInAttribute.valueOf(request.attribute());
            } catch (IllegalArgumentException e) {
                throw new WorkflowBadRequestException(
                        "Invalid fill-in scope or attribute: " + e.getMessage());
            }
            String role = principal.getAuthorities().stream().findFirst()
                    .map(Object::toString).orElse("");
            if (request.value() == null || request.value().isBlank()) {
                fillInService.delete(workflow, scope, request.key(), attribute, role);
                return ResponseEntity.noContent().build();
            }
            Gstr1FillIn fillIn = fillInService.upsert(workflow, scope, request.key(),
                    attribute, request.value(), role, principal.getUsername());
            return ResponseEntity.ok(toSummary(fillIn));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/gstr1/portal-upload")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> portalUpload(@AuthenticationPrincipal UserDetailsImpl principal,
                                          @PathVariable UUID id,
                                          @RequestBody(required = false) PortalUploadRequest request) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            workflowService.recordPortalUpload(principal.getOrganizationId(),
                    principal.getUsername(), id,
                    request == null ? null : request.getFileHash(),
                    request == null ? null : request.getNote());
            return ResponseEntity.ok(Map.of("recorded", true));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ACCOUNTANT')")
    @GetMapping("/{id}/gstr1/export")
    public ResponseEntity<?> export(@PathVariable UUID id,
                                    @RequestParam(name = "includeIrp", defaultValue = "false")
                                    boolean includeIrp,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow workflow = workflowService.get(principal.getOrganizationId(), id);
            WorkflowStage stage = workflow.getStage();
            if (stage != WorkflowStage.PREPARE && stage != WorkflowStage.FINALIZED
                    && stage != WorkflowStage.FILED) {
                throw new WorkflowConflictException("Export is available from Prepare onward");
            }
            PreparedReturn prepared = prepareService.buildOrRebuild(workflow);
            List<PreparedReturnRow> rows = rowRepository.findByPreparedReturnId(prepared.getId());
            if (stage == WorkflowStage.PREPARE) {
                boolean hasErrors = validator.validate(workflow, rows).stream()
                        .anyMatch(r -> r.severity() == ValidationResult.Severity.ERROR);
                if (hasErrors) {
                    throw new WorkflowConflictException(
                            "Export is available once validation errors are resolved");
                }
            }
            String gstin = organizationRepository.findById(workflow.getOrganizationId())
                    .map(Organization::getGstin).orElse(null);
            List<PortalFilePart> parts = jsonWriter.write(prepared, rows, workflow, gstin,
                    profileFor(workflow), new WriterOptions(includeIrp));

            byte[] body;
            String filename;
            String contentType;
            if (parts.size() == 1) {
                body = parts.get(0).bytes();
                filename = parts.get(0).fileName();
                contentType = "application/json";
            } else {
                body = zip(parts);
                String fp = filingPeriod(workflow);
                String gstinSuffix = gstin == null ? "NOGSTIN"
                        : gstin.substring(Math.max(0, gstin.length() - 6));
                filename = "GSTR1_" + fp + "_" + gstinSuffix + ".zip";
                contentType = "application/zip";
            }
            String sha256 = sha256(body);
            workflowService.recordPortalFileDownload(principal.getOrganizationId(),
                    principal.getUsername(), id, sha256, (long) body.length,
                    Gstr1WriterVersion.PORTAL_VERSION, parts.size());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(body);
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- response assembly ----------

    // ---------- export helpers ----------

    private FilerProfile profileFor(Workflow workflow) {
        List<FilerProfile> profiles = filerProfileRepository
                .findAllByOrganizationIdOrderByEffectiveFromAsc(workflow.getOrganizationId());
        LocalDate periodEnd = workflow.getPeriodEnd();
        FilerProfile applicable = null;
        for (FilerProfile profile : profiles) {
            if (profile.getEffectiveFrom() == null || periodEnd == null
                    || !profile.getEffectiveFrom().isAfter(periodEnd)) {
                applicable = profile;
            }
        }
        return applicable;
    }

    private byte[] zip(List<PortalFilePart> parts) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (PortalFilePart part : parts) {
                zip.putNextEntry(new ZipEntry(part.fileName()));
                zip.write(part.bytes());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to zip GSTR-1 portal parts", e);
        }
    }

    private String filingPeriod(Workflow workflow) {
        return java.time.format.DateTimeFormatter.ofPattern("MMyyyy")
                .format(workflow.getPeriodStart());
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Gstr1TablesResponse toResponse(Workflow workflow, PreparedReturn prepared) {
        List<PreparedReturnRow> rows = rowRepository.findByPreparedReturnId(prepared.getId());
        Map<String, List<PreparedReturnRow>> byTable = new TreeMap<>();
        for (PreparedReturnRow row : rows) {
            byTable.computeIfAbsent(row.getTableCode(), k -> new ArrayList<>()).add(row);
        }
        List<Gstr1TablesResponse.TableSummary> tables = new ArrayList<>();
        for (Map.Entry<String, List<PreparedReturnRow>> entry : byTable.entrySet()) {
            tables.add(new Gstr1TablesResponse.TableSummary(
                    entry.getKey(), entry.getValue().size(), totals(entry.getValue())));
        }
        List<Gstr1TablesResponse.FillInSummary> fillIns = fillInRepository
                .findByWorkflowId(workflow.getId()).stream().map(this::toSummary).toList();
        return new Gstr1TablesResponse(workflow.getId(), workflow.getStage().name(),
                prepared.getBuiltAt(), prepared.getFrozenAt(), tables, fillIns);
    }

    private Map<String, BigDecimal> totals(List<PreparedReturnRow> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String key : TOTAL_KEYS) {
            BigDecimal sum = BigDecimal.ZERO;
            for (PreparedReturnRow row : rows) {
                BigDecimal value = numeric(row, key);
                if (value != null) {
                    sum = sum.add(value);
                }
            }
            totals.put(key, sum);
        }
        return totals;
    }

    private BigDecimal numeric(PreparedReturnRow row, String key) {
        Object value = row.payload().get(key);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Gstr1TablesResponse.FillInSummary toSummary(Gstr1FillIn fillIn) {
        boolean fixed = !CARRY_FORWARD_ACTOR.equals(fillIn.getEnteredBy());
        return new Gstr1TablesResponse.FillInSummary(
                fillIn.getScope().name(), fillIn.getKey(), fillIn.getAttribute().name(),
                fillIn.getValue(), fillIn.getEnteredBy(), fillIn.getEnteredAt(), fixed);
    }
}
