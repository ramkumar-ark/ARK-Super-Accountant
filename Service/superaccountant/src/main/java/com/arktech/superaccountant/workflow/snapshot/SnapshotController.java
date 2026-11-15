package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewActor;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only change list per day-book upload and amendment follow-up views.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class SnapshotController {

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private com.arktech.superaccountant.masters.repository.UploadJobRepository uploadJobRepository;

    @GetMapping("/day-book/uploads/{uploadId}/changes")
    public ResponseEntity<?> changes(@AuthenticationPrincipal UserDetailsImpl principal,
                                     @PathVariable UUID uploadId) {
        UUID orgId = principal.getOrganizationId();
        DayBookUploadJob job = dayBookUploadJobRepository.findById(uploadId).orElse(null);
        if (job == null || orgId == null || !job.getOrganizationId().equals(orgId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Upload not found"));
        }
        List<Map<String, Object>> changes = new ArrayList<>();
        tamperChangeRepository.findByUploadJobId(uploadId).stream()
                .sorted(Comparator
                        .comparing((TamperChange c) -> !c.isBlocking())
                        .thenComparing(c -> c.getOldDate() != null ? c.getOldDate()
                                : c.getNewDate() != null ? c.getNewDate() : java.time.LocalDate.MAX))
                .forEach(c -> changes.add(view(c)));
        return ResponseEntity.ok(Map.of("changes", changes));
    }

    private Map<String, Object> view(TamperChange c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("changeKind", c.getChangeKind() == null ? null : c.getChangeKind().name());
        map.put("voucherKey", c.getVoucherKey());
        map.put("voucherTypeName", c.getVoucherTypeName());
        map.put("voucherNumber", c.getVoucherNumber());
        map.put("partyLedgerName", c.getPartyLedgerName());
        map.put("oldDate", c.getOldDate());
        map.put("newDate", c.getNewDate());
        map.put("touchedScopes", VoucherScopeClassifier.touchedScopesList(c.getTouchedScopes()));
        map.put("blocking", c.isBlocking());
        return map;
    }

    @GetMapping("/masters/uploads/{uploadId}/changes")
    public ResponseEntity<?> mastersChanges(@AuthenticationPrincipal UserDetailsImpl principal,
                                            @PathVariable UUID uploadId) {
        UUID orgId = principal.getOrganizationId();
        com.arktech.superaccountant.masters.models.UploadJob job =
                uploadJobRepository.findById(uploadId).orElse(null);
        if (job == null || orgId == null || !job.getOrganizationId().equals(orgId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Upload not found"));
        }
        List<Map<String, Object>> changes = new ArrayList<>();
        tamperChangeRepository.findByUploadJobId(uploadId).stream()
                .sorted(Comparator.comparing(c -> c.getCreatedAt() == null
                        ? java.time.Instant.MIN : c.getCreatedAt()))
                .forEach(c -> changes.add(masterView(c)));
        return ResponseEntity.ok(Map.of("changes", changes));
    }

    private Map<String, Object> masterView(TamperChange c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("changeKind", c.getChangeKind() == null ? null : c.getChangeKind().name());
        map.put("ledgerName", c.getLedgerName());
        map.put("attributeName", c.getAttributeName());
        map.put("oldValue", c.getOldValue());
        map.put("newValue", c.getNewValue());
        map.put("touchedScopes", VoucherScopeClassifier.touchedScopesList(c.getTouchedScopes()));
        map.put("blocking", c.isBlocking());
        map.put("createdAt", c.getCreatedAt());
        return map;
    }

    @GetMapping("/workflows/{id}/amendments")
    public ResponseEntity<?> amendments(@AuthenticationPrincipal UserDetailsImpl principal,
                                        @PathVariable UUID id) {
        List<Map<String, Object>> followUps = new ArrayList<>();
        amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(id).stream()
                .filter(f -> principal.getOrganizationId() != null
                        && f.getOrganizationId().equals(principal.getOrganizationId()))
                .forEach(f -> followUps.add(view(f)));
        return ResponseEntity.ok(Map.of("followUps", followUps));
    }

    private Map<String, Object> view(AmendmentFollowUp f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", f.getId());
        map.put("voucherKey", f.getVoucherKey());
        map.put("summary", f.getSummary());
        map.put("status", f.getStatus() == null ? null : f.getStatus().name());
        map.put("doneBy", f.getDoneBy());
        map.put("doneAt", f.getDoneAt());
        map.put("doneNote", f.getDoneNote());
        map.put("createdAt", f.getCreatedAt());
        return map;
    }

    @PostMapping("/workflows/{id}/amendments/{aid}/done")
    @Transactional
    public ResponseEntity<?> markDone(@AuthenticationPrincipal UserDetailsImpl principal,
                                      @PathVariable UUID id,
                                      @PathVariable UUID aid,
                                      @RequestBody Map<String, String> body) {
        if (!ReviewActor.of(principal).accountant()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Only an accountant can close an amendment follow-up."));
        }
        String note = body == null ? null : body.get("note");
        if (note == null || note.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A note is required."));
        }
        AmendmentFollowUp f = amendmentFollowUpRepository.findById(aid).orElse(null);
        if (f == null || principal.getOrganizationId() == null
                || !f.getOrganizationId().equals(principal.getOrganizationId())
                || !f.getWorkflowId().equals(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Follow-up not found"));
        }
        if (f.getStatus() == AmendmentStatus.DONE) {
            return ResponseEntity.status(409).body(Map.of("error", "Follow-up is already done."));
        }
        f.setStatus(AmendmentStatus.DONE);
        f.setDoneBy(principal.getUsername());
        f.setDoneAt(Instant.now());
        f.setDoneNote(note);
        amendmentFollowUpRepository.save(f);
        return ResponseEntity.ok(view(f));
    }

    @ExceptionHandler(FindingReviewException.class)
    public ResponseEntity<?> handleReviewException(FindingReviewException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
