package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FindingAutoResolveService {

    @Autowired
    private ValidationFindingRepository findingRepository;

    @Autowired
    private FindingOverrideMasterService findingOverrideMasterService;

    public List<ValidationFinding> autoResolveForUpdatedSnapshot(
            UUID uploadJobId, UploadLedgerSnapshot snapshot, String username, boolean isAccountant, UUID orgId) {

        List<ValidationFinding> openFindings = findingRepository
                .findOpenByUploadJobIdAndLedgerName(uploadJobId, snapshot.getLedgerName());

        if (openFindings.isEmpty()) return List.of();

        Instant now = Instant.now();
        List<ValidationFinding> resolved = new ArrayList<>();

        for (ValidationFinding finding : openFindings) {
            if (!isResolvedBySnapshot(finding, snapshot)) continue;
            if (isAccountant) {
                finding.setResolveStatus(ResolveStatus.RESOLVED);
                finding.setResolvedBy(username);
                finding.setResolvedAt(now);
                finding.setResolveNote("Auto-resolved: ledger data corrected inline.");
                resolved.add(finding);
            } else if (submitForReview(finding, snapshot, username, orgId, now)) {
                resolved.add(finding);
            }
        }

        if (!resolved.isEmpty()) {
            findingRepository.saveAll(resolved);
        }

        return resolved;
    }

    private boolean submitForReview(ValidationFinding finding, UploadLedgerSnapshot snapshot, String username,
                                    UUID orgId, Instant now) {
        String previousActual = finding.getActualValue();
        String value = null;
        if (FindingOverrideMasterService.TYPED_INPUT_RULES.contains(finding.getRuleCode())) {
            value = typedValue(finding.getRuleCode(), snapshot);
        } else {
            finding.setActualValue(correctedActual(finding.getMismatchType(), snapshot, previousActual));
        }
        try {
            findingOverrideMasterService.submit(finding, value,
                    "Ledger data corrected inline.", orgId, username, now);
            return true;
        } catch (FindingOverrideException e) {
            finding.setActualValue(previousActual);
            return false;
        }
    }

    private String typedValue(String ruleCode, UploadLedgerSnapshot snapshot) {
        return switch (ruleCode) {
            case "TDS_SECTION_MAPPING" -> snapshot.getTdsSection();
            case "HSN_SAC_CODE" -> snapshot.getHsnSacCode();
            case "GSTIN_PRESENCE" -> snapshot.getGstin();
            case "GST_APPLICABILITY" -> snapshot.getGstApplicabilityType() == null
                    ? null : snapshot.getGstApplicabilityType().name();
            default -> null;
        };
    }

    private String correctedActual(MismatchType type, UploadLedgerSnapshot snapshot, String fallback) {
        return switch (type) {
            case PARENT_GROUP_MISMATCH -> snapshot.getParentGroup();
            case GST_APPLICABILITY_MISMATCH -> String.valueOf(snapshot.getGstApplicable());
            case TDS_APPLICABILITY_MISMATCH -> String.valueOf(snapshot.getTdsApplicable());
            default -> fallback;
        };
    }

    private boolean isResolvedBySnapshot(ValidationFinding finding, UploadLedgerSnapshot snapshot) {
        if (finding.getRuleCode() != null) {
            return switch (finding.getRuleCode()) {
                case "TDS_SECTION_MAPPING" ->
                        snapshot.getTdsSection() != null && !snapshot.getTdsSection().isBlank();
                case "HSN_SAC_CODE" ->
                        snapshot.getHsnSacCode() != null && !snapshot.getHsnSacCode().isBlank();
                case "GSTIN_PRESENCE" ->
                        snapshot.getGstin() != null && !snapshot.getGstin().isBlank();
                case "GST_APPLICABILITY" ->
                        snapshot.getGstApplicabilityType() != null;
                default -> false;
            };
        }
        if (finding.getMismatchType() == null) return false;
        return switch (finding.getMismatchType()) {
            case PARENT_GROUP_MISMATCH ->
                    normalize(snapshot.getParentGroup()).equals(normalize(finding.getExpectedValue()));
            case GST_APPLICABILITY_MISMATCH ->
                    String.valueOf(snapshot.getGstApplicable()).equals(finding.getExpectedValue());
            case TDS_APPLICABILITY_MISMATCH ->
                    String.valueOf(snapshot.getTdsApplicable()).equals(finding.getExpectedValue());
            default -> false;
        };
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
