package com.arktech.superaccountant.workflow.snapshot;

import java.util.UUID;

public interface TallyUploadReconciliation {

    void reconcile(UUID uploadJobId);
}
