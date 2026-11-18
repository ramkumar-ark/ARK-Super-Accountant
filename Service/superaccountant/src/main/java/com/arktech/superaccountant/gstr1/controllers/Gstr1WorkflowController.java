package com.arktech.superaccountant.gstr1.controllers;

import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.payload.request.Gstr1FillInRequest;
import com.arktech.superaccountant.gstr1.payload.response.Gstr1TablesResponse;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.Gstr1FillInService;
import com.arktech.superaccountant.gstr1.services.Gstr1PrepareService;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.workflow.models.Workflow;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

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

    @PreAuthorize("hasRole('ACCOUNTANT')")
    @PutMapping("/{id}/gstr1/fill-ins")
    public ResponseEntity<?> putFillIn(@PathVariable UUID id,
                                       @RequestBody Gstr1FillInRequest request,
                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow workflow = workflowService.get(principal.getOrganizationId(), id);
            Gstr1FillInScope scope = Gstr1FillInScope.valueOf(request.scope());
            Gstr1FillInAttribute attribute = Gstr1FillInAttribute.valueOf(request.attribute());
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

    // ---------- response assembly ----------

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
