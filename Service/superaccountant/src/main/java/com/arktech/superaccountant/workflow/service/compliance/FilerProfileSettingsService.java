package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.request.FilerProfileRequest;
import com.arktech.superaccountant.workflow.payload.response.FilerProfileResponse;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FilerProfileSettingsService {

    @Autowired
    private FilerProfileRepository filerProfileRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ResponsibilityStartSettingsService responsibilityStartSettingsService;

    public List<FilerProfileResponse> list(UUID organizationId) {
        return filerProfileRepository.findAllByOrganizationIdOrderByEffectiveFromAsc(organizationId)
                .stream()
                .map(FilerProfileSettingsService::toResponse)
                .toList();
    }

    @Transactional
    public FilerProfileResponse put(UUID organizationId, String actor, FilerProfileRequest request) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        FilerFrequency frequency;
        try {
            frequency = FilerFrequency.valueOf(request.getFrequency());
        } catch (IllegalArgumentException e) {
            throw new WorkflowBadRequestException("Unknown frequency: " + request.getFrequency());
        }
        LocalDate effectiveFrom = request.getEffectiveFrom();
        if (!PeriodCalendar.isQuarterStart(effectiveFrom, org.getFinancialYearStart())) {
            throw new WorkflowBadRequestException(
                    "effectiveFrom must be a quarter start for this financial year");
        }
        int hsnDigits = request.getHsnDigits() == null ? 4 : request.getHsnDigits();
        if (hsnDigits != 4 && hsnDigits != 6) {
            throw new WorkflowBadRequestException("hsnDigits must be 4 or 6");
        }
        for (WorkflowType type : List.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF)) {
            boolean conflicts = workflowRepository
                    .findAllByOrganizationIdAndTypeAndStageNot(organizationId, type, WorkflowStage.BASELINE)
                    .stream()
                    .anyMatch(w -> w.isActive() && !w.getPeriodStart().isBefore(effectiveFrom));
            if (conflicts) {
                throw new WorkflowConflictException(
                        "GST workflows already started on or after " + effectiveFrom);
            }
        }

        FilerProfile profile = filerProfileRepository
                .findByOrganizationIdAndEffectiveFrom(organizationId, effectiveFrom)
                .orElseGet(() -> {
                    FilerProfile p = new FilerProfile();
                    p.setOrganizationId(organizationId);
                    p.setEffectiveFrom(effectiveFrom);
                    return p;
                });
        profile.setFrequency(frequency);
        profile.setIffOptIn(request.isIffOptIn());
        profile.setEinvoiceEnabled(request.isEinvoiceEnabled());
        profile.setHsnDigits(hsnDigits);
        profile.setCreatedBy(actor);
        filerProfileRepository.save(profile);

        for (WorkflowType type : List.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF)) {
            responsibilityStartSettingsService.list(organizationId).stream()
                    .filter(r -> r.type().equals(type.name()) && r.firstPeriodStart() != null)
                    .filter(r -> r.firstPeriodStart().isAfter(effectiveFrom))
                    .findFirst()
                    .ifPresent(r -> responsibilityStartSettingsService
                            .rebuildBaselineRows(organizationId, actor, type));
        }
        return toResponse(profile);
    }

    private static FilerProfileResponse toResponse(FilerProfile p) {
        return new FilerProfileResponse(p.getId(), p.getFrequency().name(), p.isIffOptIn(),
                p.isEinvoiceEnabled(), p.getHsnDigits(), p.getEffectiveFrom(), p.getCreatedBy(), p.getCreatedAt());
    }
}
