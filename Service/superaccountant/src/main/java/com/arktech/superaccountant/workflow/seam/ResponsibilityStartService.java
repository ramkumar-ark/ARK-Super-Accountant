package com.arktech.superaccountant.workflow.seam;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Seam for the onboarding worker (O): earliest date a company's data is
 * trusted (responsibility start). No-op default imposes no restriction.
 */
@Service
public class ResponsibilityStartService {
    public Optional<LocalDate> responsibilityStart(UUID organizationId) {
        return Optional.empty();
    }
}
