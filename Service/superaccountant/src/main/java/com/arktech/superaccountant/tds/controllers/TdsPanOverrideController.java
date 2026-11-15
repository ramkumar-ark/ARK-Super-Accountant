package com.arktech.superaccountant.tds.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.payload.response.TdsPanOverrideResponse;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/tds/pan-overrides")
public class TdsPanOverrideController {

    private final TdsPanOverrideRepository panOverrideRepository;
    private final UploadLedgerSnapshotRepository snapshotRepository;
    private final MastersJobGate mastersJobGate;

    public TdsPanOverrideController(TdsPanOverrideRepository panOverrideRepository,
                                    UploadLedgerSnapshotRepository snapshotRepository,
                                    MastersJobGate mastersJobGate) {
        this.panOverrideRepository = panOverrideRepository;
        this.snapshotRepository = snapshotRepository;
        this.mastersJobGate = mastersJobGate;
    }

    @GetMapping
    public List<TdsPanOverrideResponse> list(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        Map<String, UploadLedgerSnapshot> mastersByName = new LinkedHashMap<>();
        mastersJobGate.latestCompletedMastersJob(orgId).ifPresent(jobId ->
                snapshotRepository.findByUploadJobId(jobId)
                        .forEach(s -> mastersByName.put(TdsEngine.key(s.getLedgerName()), s)));
        return panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(orgId)
                .stream().map(o -> toResponse(o, mastersByName.get(o.getPartyKey())))
                .toList();
    }

    private TdsPanOverrideResponse toResponse(TdsPanOverride o, UploadLedgerSnapshot masters) {
        String mastersPan = masters == null ? null : masters.getPan();
        boolean mastersDiffers = mastersPan != null && !mastersPan.isBlank()
                && mastersPan.matches(TdsEngine.PAN_PATTERN)
                && !mastersPan.equals(o.getPan());
        return new TdsPanOverrideResponse(o.getPartyLedgerName(), o.getPan(), o.getApprovedBy(),
                o.getApprovedAt(), mastersPan, mastersDiffers);
    }
}
