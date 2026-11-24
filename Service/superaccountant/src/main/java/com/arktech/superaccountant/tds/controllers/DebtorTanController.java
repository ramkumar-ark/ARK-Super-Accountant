package com.arktech.superaccountant.tds.controllers;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.tan.DebtorTanGateway;
import com.arktech.superaccountant.tds.engine.DebtorTdsReceivableScanner;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingService;
import com.arktech.superaccountant.tds.models.DebtorTan;
import com.arktech.superaccountant.tds.payload.request.DebtorTanRequest;
import com.arktech.superaccountant.tds.payload.response.DebtorTanResponse;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Locale.ROOT;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/tds/debtor-tans")
public class DebtorTanController {

    private final DebtorTanGateway debtorTanGateway;
    private final UploadLedgerSnapshotRepository snapshotRepository;
    private final MastersJobGate mastersJobGate;
    private final DayBookUploadJobRepository dayBookUploadJobRepository;
    private final ParsedVoucherRepository parsedVoucherRepository;
    private final TdsLedgerMappingService tdsLedgerMappingService;

    public DebtorTanController(DebtorTanGateway debtorTanGateway,
                               UploadLedgerSnapshotRepository snapshotRepository,
                               MastersJobGate mastersJobGate,
                               DayBookUploadJobRepository dayBookUploadJobRepository,
                               ParsedVoucherRepository parsedVoucherRepository,
                               TdsLedgerMappingService tdsLedgerMappingService) {
        this.debtorTanGateway = debtorTanGateway;
        this.snapshotRepository = snapshotRepository;
        this.mastersJobGate = mastersJobGate;
        this.dayBookUploadJobRepository = dayBookUploadJobRepository;
        this.parsedVoucherRepository = parsedVoucherRepository;
        this.tdsLedgerMappingService = tdsLedgerMappingService;
    }

    @GetMapping
    public List<DebtorTanResponse> list(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        Map<String, UploadLedgerSnapshot> debtorsByKey = latestDebtorsByKey(orgId);
        List<DebtorTan> stored = debtorTanGateway.findAll(orgId);
        Map<String, DebtorTan> storedByKey = stored.stream()
                .collect(Collectors.toMap(DebtorTan::getLedgerKey, r -> r, (a, b) -> a));
        Map<String, DebtorTan> storedByGuid = stored.stream()
                .filter(r -> r.getTallyGuid() != null)
                .collect(Collectors.toMap(DebtorTan::getTallyGuid, r -> r, (a, b) -> a));

        Map<String, DebtorTanResponse> responses = new LinkedHashMap<>();
        for (UploadLedgerSnapshot snapshot : debtorsByKey.values()) {
            String key = TdsEngine.key(snapshot.getLedgerName());
            DebtorTan match = storedByKey.get(key);
            if (match == null && snapshot.getTallyGuid() != null) {
                match = storedByGuid.get(snapshot.getTallyGuid());
            }
            responses.put(key, toResponse(snapshot, match, true));
        }
        for (DebtorTan record : stored) {
            responses.putIfAbsent(record.getLedgerKey(), toResponse(null, record, false));
        }
        return responses.values().stream()
                .sorted(Comparator.comparing(DebtorTanResponse::ledgerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GetMapping("/missing")
    public List<DebtorTdsReceivableScanner.DebtorTanGap> missing(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        Optional<DayBookUploadJob> dayBookJob = dayBookUploadJobRepository
                .findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                        java.util.EnumSet.of(UploadJobStatus.COMPLETED,
                                UploadJobStatus.COMPLETED_WITH_MISMATCHES));
        if (dayBookJob.isEmpty()) {
            return List.of();
        }
        Set<String> debtorKeys = new HashSet<>();
        mastersJobGate.latestCompletedMastersJob(orgId).ifPresent(mastersJobId -> {
            for (UploadLedgerSnapshot snapshot : snapshotRepository.findByUploadJobId(mastersJobId)) {
                if (isDebtor(snapshot)) {
                    debtorKeys.add(TdsEngine.key(snapshot.getLedgerName()));
                }
            }
        });
        Map<String, String> tansByKey = new HashMap<>();
        for (DebtorTan record : debtorTanGateway.findAll(orgId)) {
            tansByKey.putIfAbsent(record.getLedgerKey(), record.getTan());
        }
        List<DebtorTdsReceivableScanner.DebtorTanGap> gaps = DebtorTdsReceivableScanner.scan(
                parsedVoucherRepository.findByUploadJobId(dayBookJob.get().getId()),
                debtorKeys, tdsLedgerMappingService.receivableLedgerKeys(orgId), tansByKey);
        gaps.sort(Comparator.comparing(DebtorTdsReceivableScanner.DebtorTanGap::voucherDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DebtorTdsReceivableScanner.DebtorTanGap::debtorLedgerName,
                        String.CASE_INSENSITIVE_ORDER));
        return gaps;
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    public ResponseEntity<?> upsert(@Valid @RequestBody DebtorTanRequest request,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        try {
            debtorTanGateway.saveTan(orgId, request.getLedgerName(), request.getTan(),
                    request.getTallyGuid(), null, principal.getUsername(), Instant.now());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        DebtorTan record = debtorTanGateway
                .findRecord(orgId, request.getLedgerName())
                .orElseThrow();
        UploadLedgerSnapshot snapshot = latestDebtorsByKey(orgId).get(record.getLedgerKey());
        return ResponseEntity.ok(toResponse(snapshot, record, snapshot != null));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    public ResponseEntity<Void> delete(@RequestParam String ledgerName,
                                       @AuthenticationPrincipal UserDetailsImpl principal) {
        debtorTanGateway.deleteTan(principal.getOrganizationId(), ledgerName);
        return ResponseEntity.noContent().build();
    }

    private Map<String, UploadLedgerSnapshot> latestDebtorsByKey(UUID orgId) {
        Map<String, UploadLedgerSnapshot> debtors = new LinkedHashMap<>();
        mastersJobGate.latestCompletedMastersJob(orgId).ifPresent(jobId ->
                snapshotRepository.findByUploadJobId(jobId).stream()
                        .filter(DebtorTanController::isDebtor)
                        .forEach(s -> debtors.put(TdsEngine.key(s.getLedgerName()), s)));
        return debtors;
    }

    private static boolean isDebtor(UploadLedgerSnapshot snapshot) {
        String chain = snapshot.getGroupChain();
        return chain != null && chain.toLowerCase(ROOT).contains("sundry debtors");
    }

    private static DebtorTanResponse toResponse(UploadLedgerSnapshot snapshot, DebtorTan record,
                                                boolean inLatestUpload) {
        if (record != null) {
            String ledgerName = snapshot == null ? record.getLedgerName() : snapshot.getLedgerName();
            String ledgerKey = snapshot == null ? record.getLedgerKey() : TdsEngine.key(snapshot.getLedgerName());
            String tallyGuid = record.getTallyGuid() != null ? record.getTallyGuid() : snapshot == null ? null : snapshot.getTallyGuid();
            return new DebtorTanResponse(ledgerName, ledgerKey, tallyGuid, record.getTan(),
                    record.getSourceFindingId(), record.getUpdatedBy(), record.getUpdatedAt(),
                    inLatestUpload);
        }
        return new DebtorTanResponse(snapshot.getLedgerName(), TdsEngine.key(snapshot.getLedgerName()),
                snapshot.getTallyGuid(), null, null, null, null, inLatestUpload);
    }
}
