package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.FinalizePrecondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment-month Finalize gate (grill Q3): at least one APPROVED challan (or an approved nil
 * challan when nothing was deducted); blocks when approved challan TDS is less than TDS
 * deducted. An excess is allowed.
 */
@Component
public class TdsPaymentFinalizePrecondition implements FinalizePrecondition {

    private final TdsChallanRepository challanRepository;
    private final TdsPaymentDetailRepository paymentDetailRepository;

    public TdsPaymentFinalizePrecondition(TdsChallanRepository challanRepository,
                                          TdsPaymentDetailRepository paymentDetailRepository) {
        this.challanRepository = challanRepository;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return w.getType() == WorkflowType.TDS_PAYMENT;
    }

    @Override
    public List<String> unmet(Workflow w) {
        List<String> unmet = new ArrayList<>();

        TdsPaymentDetail detail = paymentDetailRepository.findByWorkflowId(w.getId()).orElse(null);
        BigDecimal deducted = detail == null ? null : detail.getTdsDeducted();
        if (deducted == null) {
            unmet.add("TDS deducted for the month has not been computed; run analysis");
        }

        List<TdsChallan> challans = challanRepository
                .findByPaymentWorkflowIdOrderByDepositDateAsc(w.getId());
        List<TdsChallan> approved = challans.stream()
                .filter(c -> c.getStatus() == TdsChallanStatus.APPROVED).toList();
        if (approved.isEmpty()) {
            unmet.add("At least one approved challan (or an approved nil challan) is required");
        }
        long proposed = challans.stream()
                .filter(c -> c.getStatus() == TdsChallanStatus.PROPOSED).count();
        if (proposed >= 1) {
            unmet.add(proposed + " challan(s) awaiting accountant approval");
        }
        if (deducted != null) {
            BigDecimal approvedTotal = approved.stream().map(TdsChallan::getTdsAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (approvedTotal.compareTo(deducted) < 0) {
                BigDecimal diff = deducted.subtract(approvedTotal)
                        .setScale(2, java.math.RoundingMode.UNNECESSARY);
                unmet.add("Approved challans cover " + money(approvedTotal) + " of "
                        + money(deducted) + " TDS deducted (short by " + money(diff) + ")");
            }
        }
        return unmet;
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
