package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.PreconfiguredMaster;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.review.FindingApplier;

import java.util.Map;
import java.util.UUID;

/**
 * Adapts {@link FindingOverrideMasterService} to the shared {@link FindingApplier} SPI. A plain
 * object, not a bean: the controller builds one per action, holding the batch maps when present.
 */
public class MastersFindingApplier implements FindingApplier<ValidationFinding> {

    private final FindingOverrideMasterService svc;
    private final PanFormatOverrideService panFormat;
    private final UUID orgId;
    private final Map<String, PreconfiguredMaster> masters;
    private final Map<String, UploadLedgerSnapshot> snapshots;

    public MastersFindingApplier(FindingOverrideMasterService svc, PanFormatOverrideService panFormat, UUID orgId,
                                 Map<String, PreconfiguredMaster> masters,
                                 Map<String, UploadLedgerSnapshot> snapshots) {
        this.svc = svc;
        this.panFormat = panFormat;
        this.orgId = orgId;
        this.masters = masters;
        this.snapshots = snapshots;
    }

    @Override
    public void validate(ValidationFinding finding, String proposedValue, String note) {
        if (PanFormat.RULE_CODE.equals(finding.getRuleCode())) {
            panFormat.validate(proposedValue);
            return;
        }
        svc.validate(finding, proposedValue, orgId, snapshots);
    }

    @Override
    public void apply(ValidationFinding finding, String approver) {
        if (PanFormat.RULE_CODE.equals(finding.getRuleCode())) {
            panFormat.apply(finding, orgId, approver);
            return;
        }
        if (masters == null) {
            svc.apply(finding, orgId, approver);
        } else {
            svc.apply(finding, orgId, approver, masters, snapshots);
        }
    }
}
