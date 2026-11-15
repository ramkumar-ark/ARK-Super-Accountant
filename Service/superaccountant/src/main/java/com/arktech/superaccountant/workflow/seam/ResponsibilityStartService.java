package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Seam for the onboarding worker (O): earliest date a company's data is
 * trusted for a return type (responsibility start). No row imposes no
 * restriction for that type.
 */
@Service
public class ResponsibilityStartService {

    @Autowired
    private ResponsibilityStartRepository responsibilityStartRepository;

    public Optional<LocalDate> responsibilityStart(UUID organizationId, WorkflowType type) {
        return responsibilityStartRepository.findByOrganizationIdAndType(organizationId, type)
                .map(com.arktech.superaccountant.workflow.models.ResponsibilityStart::getFirstPeriodStart);
    }
}
