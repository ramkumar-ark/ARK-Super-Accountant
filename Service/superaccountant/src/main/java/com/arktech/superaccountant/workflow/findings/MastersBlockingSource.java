package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Masters findings block every workflow type, at every severity, until they are terminal
 * (APPROVED, RESOLVED or DISCARDED) — the C2 rule. A workflow without a bound masters job
 * has a blocking count of 0.
 */
@Component
public class MastersBlockingSource implements BlockingFindingSource {

    private final ValidationFindingRepository findingRepository;

    public MastersBlockingSource(ValidationFindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return true;
    }

    @Override
    public long blockingCount(Workflow w) {
        if (w.getMastersJobId() == null) {
            return 0;
        }
        return findingRepository.countByUploadJobIdAndResolveStatusNotIn(
                w.getMastersJobId(), EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED,
                        ResolveStatus.DISCARDED));
    }
}
