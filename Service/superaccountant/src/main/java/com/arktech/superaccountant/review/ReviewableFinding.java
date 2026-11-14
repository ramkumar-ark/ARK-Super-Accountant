package com.arktech.superaccountant.review;

import com.arktech.superaccountant.masters.models.ResolveStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The review state shared by every module's finding entity: masters' {@code ValidationFinding}
 * today, the GST and TDS finding tables later. Getters and setters follow Lombok's bean shape so
 * {@code @Data} entities implement the interface without extra methods.
 */
public interface ReviewableFinding {
    UUID getId();

    ResolveStatus getResolveStatus();

    void setResolveStatus(ResolveStatus s);

    String getResolveNote();

    void setResolveNote(String s);

    String getProposedValue();

    void setProposedValue(String s);

    String getSubmittedBy();

    void setSubmittedBy(String s);

    Instant getSubmittedAt();

    void setSubmittedAt(Instant t);

    String getReviewedBy();

    void setReviewedBy(String s);

    Instant getReviewedAt();

    void setReviewedAt(Instant t);

    String getReviewNote();

    void setReviewNote(String s);

    String getResolvedBy();

    void setResolvedBy(String s);

    Instant getResolvedAt();

    void setResolvedAt(Instant t);
}
