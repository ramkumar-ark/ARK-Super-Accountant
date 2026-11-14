package com.arktech.superaccountant.review;

/** What one reconcile pass did to the previous and fresh finding sets. */
public record LifecycleOutcome(int carried, int reopened, int fresh, int autoResolved, int discarded) {
}
