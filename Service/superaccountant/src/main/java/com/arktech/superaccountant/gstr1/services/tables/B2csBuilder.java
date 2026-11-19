package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.Gstr1Rules;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class B2csBuilder {

    private static final class Bucket {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal cess = BigDecimal.ZERO;
        BigDecimal invoiceValue = BigDecimal.ZERO;
        String pos;
        BigDecimal rate;
    }

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        Map<String, Bucket> buckets = new HashMap<>();
        YearMonth period = YearMonth.from(workflow.getPeriodStart());
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isNote(v.getVoucherTypeName())
                    || TableRows.hasGstin(v) || isExportCandidate(v) || isNilRate(v)) {
                continue;
            }
            String pos = v.placeOfSupplyCode();
            String supplier = v.getSupplierStateCode();
            BigDecimal value = TableRows.orZero(v.getTotalValue());
            if (pos == null || supplier == null) {
                continue;
            }
            boolean interState = !pos.equals(supplier);
            if (interState && value.compareTo(Gstr1Rules.b2clThreshold(period)) > 0) {
                continue;
            }
            BigDecimal rate = v.getRate() == null ? BigDecimal.ZERO : v.getRate();
            String key = pos + "@" + rate.toPlainString();
            Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
            bucket.pos = pos;
            bucket.rate = rate;
            bucket.taxable = bucket.taxable.add(TableRows.orZero(v.getTaxableValue()));
            bucket.igst = bucket.igst.add(TableRows.orZero(v.getIgst()));
            bucket.cgst = bucket.cgst.add(TableRows.orZero(v.getCgst()));
            bucket.sgst = bucket.sgst.add(TableRows.orZero(v.getSgst()));
            bucket.cess = bucket.cess.add(TableRows.orZero(v.getCess()));
            bucket.invoiceValue = bucket.invoiceValue.add(value);
        }
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            Map<String, Object> payload = TableRows.payload();
            payload.put("pos", bucket.pos);
            payload.put("rate", bucket.rate);
            payload.put("taxableValue", bucket.taxable);
            payload.put("igst", bucket.igst);
            payload.put("cgst", bucket.cgst);
            payload.put("sgst", bucket.sgst);
            payload.put("cess", bucket.cess);
            payload.put("invoiceValue", bucket.invoiceValue);
            rows.add(TableRows.row(workflow.getId(), "B2CS",
                    "b2cs:" + bucket.pos + ":" + bucket.rate.toPlainString(), null, payload));
        }
        return rows;
    }

    private boolean isNilRate(VoucherView v) {
        return v.getRate() != null && v.getRate().compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isExportCandidate(VoucherView v) {
        String nature = TableRows.lower(v.getSupplyNature());
        String type = TableRows.lower(v.getTypeOfSupply());
        boolean foreign = !TableRows.isBlank(v.getConsigneeCountry())
                && !"india".equals(TableRows.lower(v.getConsigneeCountry()));
        return nature.contains("export") || nature.equals("sez")
                || type.contains("export") || type.equals("sez") || foreign;
    }
}
