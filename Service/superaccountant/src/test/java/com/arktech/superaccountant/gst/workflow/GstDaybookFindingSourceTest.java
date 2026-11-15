package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GstDaybookFindingSourceTest {

    GstDaybookFindingRepository repository;
    GstDaybookFindingSource source;
    Workflow gstr3b;

    @BeforeEach
    void setUp() {
        repository = mock(GstDaybookFindingRepository.class);
        source = new GstDaybookFindingSource(repository);
        gstr3b = new Workflow();
        gstr3b.setId(UUID.randomUUID());
        gstr3b.setType(WorkflowType.GSTR3B);
    }

    private GstDaybookFinding finding(Workflow w, FindingSeverity severity, ResolveStatus status) {
        GstDaybookFinding f = new GstDaybookFinding();
        f.setId(UUID.randomUUID());
        f.setWorkflowId(w.getId());
        f.setFindingType(GstFindingType.GST_NOT_CHARGED);
        f.setSeverity(severity);
        f.setResolveStatus(status);
        f.setMessage("m");
        f.setVoucherDate(LocalDate.of(2025, 4, 10));
        return f;
    }

    @Test
    void view_highOpenIsBlocking_mediumOpenIsNot() {
        WorkflowFindingView high = source.view(finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.OPEN));
        WorkflowFindingView medium = source.view(
                finding(gstr3b, FindingSeverity.MEDIUM, ResolveStatus.OPEN));

        assertThat(high.source()).isEqualTo("GST");
        assertThat(high.findingType()).isEqualTo("GST_NOT_CHARGED");
        assertThat(high.severity()).isEqualTo("HIGH");
        assertThat(high.blocking()).isTrue();

        assertThat(medium.severity()).isEqualTo("MEDIUM");
        assertThat(medium.blocking()).isFalse();
    }

    @Test
    void view_highApprovedNotBlocking() {
        WorkflowFindingView view = source.view(
                finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.APPROVED));

        assertThat(view.blocking()).isFalse();
    }

    @Test
    void appliesTo_onlyGstTypes() {
        Workflow tdsPayment = new Workflow();
        tdsPayment.setType(WorkflowType.TDS_PAYMENT);
        Workflow tds26q = new Workflow();
        tds26q.setType(WorkflowType.TDS_26Q);

        assertThat(source.appliesTo(gstr3b)).isTrue();

        Workflow gstr1 = new Workflow();
        gstr1.setType(WorkflowType.GSTR1);
        assertThat(source.appliesTo(gstr1)).isTrue();

        assertThat(source.appliesTo(tdsPayment)).isFalse();
        assertThat(source.appliesTo(tds26q)).isFalse();
    }

    @Test
    void lockForUpdate_otherWorkflowFinding_empty() {
        GstDaybookFinding other = finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.OPEN);
        other.setWorkflowId(UUID.randomUUID());
        when(repository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

        assertThat(source.lockForUpdate(gstr3b, other.getId())).isEmpty();
    }

    @Test
    void lockForUpdate_supersededFinding_empty() {
        GstDaybookFinding superseded = finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.OPEN);
        superseded.setSupersededAt(java.time.Instant.now());
        when(repository.findByIdForUpdate(superseded.getId())).thenReturn(Optional.of(superseded));

        assertThat(source.lockForUpdate(gstr3b, superseded.getId())).isEmpty();
    }

    @Test
    void applier_requiresNote() {
        FindingApplier<ReviewableFinding> applier = source.applier(gstr3b);

        assertThatThrownBy(() -> applier.validate(finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.OPEN),
                null, " "))
                .isInstanceOf(FindingReviewException.class);
    }

    @Test
    void sourceCode_isGst_andListMapsCurrentSet() {
        assertThat(source.sourceCode()).isEqualTo("GST");

        GstDaybookFinding current = finding(gstr3b, FindingSeverity.HIGH, ResolveStatus.OPEN);
        current.setVoucherSide(VoucherSide.PURCHASE);
        when(repository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAscCreatedAtAsc(gstr3b.getId()))
                .thenReturn(java.util.List.of(current));

        java.util.List<WorkflowFindingView> views = source.list(gstr3b);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).voucherDate()).isEqualTo(LocalDate.of(2025, 4, 10));

        Workflow tds = new Workflow();
        tds.setId(UUID.randomUUID());
        tds.setType(WorkflowType.TDS_PAYMENT);
        assertThat(source.list(tds)).isEmpty();
    }
}
