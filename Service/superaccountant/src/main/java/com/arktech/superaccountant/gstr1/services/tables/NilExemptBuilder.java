package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NilExemptBuilder {

    private static final class Bucket {
        BigDecimal taxable = BigDecimal.ZERO;
        String pos;
    }

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isNote(v.getVoucherTypeName())
                    || TableRows.hasGstin(v) || !isNilRate(v)) {
                continue;
            }
            String pos = v.placeOfSupplyCode();
            if (pos == null) {
                continue;
            }
            Bucket bucket = buckets.computeIfAbsent(pos, k -> new Bucket());
            bucket.pos = pos;
            bucket.taxable = bucket.taxable.add(TableRows.orZero(v.getTaxableValue()));
        }
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            Map<String, Object> payload = TableRows.payload();
            payload.put("pos", bucket.pos);
            payload.put("taxableValue", bucket.taxable);
            rows.add(TableRows.row(workflow.getId(), "NIL", "nil:" + bucket.pos, null, payload));
        }
        return rows;
    }

    private boolean isNilRate(VoucherView v) {
        return v.getRate() != null && v.getRate().compareTo(BigDecimal.ZERO) == 0;
    }
}
