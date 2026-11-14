package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Whether the given day-book upload is still the organization's latest
 * COMPLETED upload (owner: worker B). Finalize requires this.
 */
@Service
public class UploadFreshnessGate {

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    public boolean isStillLatest(UUID organizationId, UUID uploadJobId) {
        if (uploadJobId == null) {
            return false;
        }
        Optional<DayBookUploadJob> latest = dayBookUploadJobRepository
                .findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, UploadJobStatus.COMPLETED);
        return latest.isPresent() && latest.get().getId().equals(uploadJobId);
    }
}
