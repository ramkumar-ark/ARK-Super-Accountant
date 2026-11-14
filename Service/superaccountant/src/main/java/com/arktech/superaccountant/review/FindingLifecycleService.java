package com.arktech.superaccountant.review;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carries findings forward across a re-upload (C3(a)). Same key and same hash: the review state
 * moves to the fresh finding and the old one is DISCARDED. Same key but a different hash: the
 * fresh finding starts OPEN, linked to the old one. No match: an old non-terminal finding is
 * auto-resolved by the system. Nothing is deleted; the caller saves the mutated entities.
 */
@Service
public class FindingLifecycleService {

    /** review field copy for a carried finding. */
    private static final ResolveStatus[] TERMINAL = {ResolveStatus.APPROVED, ResolveStatus.RESOLVED,
            ResolveStatus.DISCARDED};

    public <F extends LifecycleFinding> LifecycleOutcome reconcile(List<F> previous, List<F> fresh, Instant now) {
        Map<String, F> byKey = new HashMap<>();
        Set<String> duplicateKeys = new HashSet<>();
        for (F f : fresh) {
            if (byKey.put(f.getFindingKey(), f) != null) {
                duplicateKeys.add(f.getFindingKey());
            }
        }

        int carried = 0;
        int reopened = 0;
        int freshCount = 0;
        int autoResolved = 0;
        int discarded = 0;

        for (F f : fresh) {
            if (duplicateKeys.contains(f.getFindingKey())) {
                continue; // ambiguous key: carry nothing, both start OPEN
            }
            freshCount++;
        }

        for (F p : previous) {
            F match = duplicateKeys.contains(p.getFindingKey()) ? null : byKey.get(p.getFindingKey());
            if (duplicateKeys.contains(p.getFindingKey())) {
                continue; // ambiguous: carry nothing, auto-resolve nothing
            }
            if (match == null) {
                if (!isTerminal(p)) {
                    p.setResolveStatus(ResolveStatus.RESOLVED);
                    p.setResolvedBy("system");
                    p.setResolvedAt(now);
                    p.setResolveNote("No longer present after re-upload.");
                    autoResolved++;
                }
                continue;
            }
            if (match.getEvidenceHash() != null && match.getEvidenceHash().equals(p.getEvidenceHash())) {
                copyReviewFields(p, match);
                match.setCarriedFromFindingId(p.getId());
                if (!ResolveStatus.APPROVED.equals(p.getResolveStatus())) {
                    p.setResolveStatus(ResolveStatus.DISCARDED);
                }
                carried++;
            } else {
                match.setCarriedFromFindingId(p.getId());
                if (!isTerminal(p)) {
                    p.setResolveStatus(ResolveStatus.DISCARDED);
                }
                reopened++;
            }
        }
        return new LifecycleOutcome(carried, reopened, freshCount, autoResolved, discarded);
    }

    private boolean isTerminal(ReviewableFinding f) {
        return f.getResolveStatus() == ResolveStatus.APPROVED
                || f.getResolveStatus() == ResolveStatus.RESOLVED
                || f.getResolveStatus() == ResolveStatus.DISCARDED;
    }

    private void copyReviewFields(ReviewableFinding from, ReviewableFinding to) {
        to.setResolveStatus(from.getResolveStatus());
        to.setResolveNote(from.getResolveNote());
        to.setProposedValue(from.getProposedValue());
        to.setSubmittedBy(from.getSubmittedBy());
        to.setSubmittedAt(from.getSubmittedAt());
        to.setReviewedBy(from.getReviewedBy());
        to.setReviewedAt(from.getReviewedAt());
        to.setReviewNote(from.getReviewNote());
        to.setResolvedBy(from.getResolvedBy());
        to.setResolvedAt(from.getResolvedAt());
    }
}
