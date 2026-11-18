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
public class HsnBuilder {

    private static final class Bucket {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal cess = BigDecimal.ZERO;
        BigDecimal invoiceValue = BigDecimal.ZERO;
        String hsn;
        String uqc;
        BigDecimal rate;
        String supplyType;
    }

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isBlank(v.getHsn())) {
                continue;
            }
            String hsn = v.getHsn().trim();
            String uqc = TableRows.isBlank(v.getUqc()) ? "NA" : v.getUqc().trim();
            BigDecimal rate = v.getRate() == null ? BigDecimal.ZERO : v.getRate();
            String supplyType = TableRows.hasGstin(v) ? "B2B" : "B2C";
            String key = hsn + ":" + uqc + ":" + rate.toPlainString() + ":" + supplyType;
            Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
            bucket.hsn = hsn;
            bucket.uqc = uqc;
            bucket.rate = rate;
            bucket.supplyType = supplyType;
            bucket.quantity = bucket.quantity.add(TableRows.orZero(v.getQuantity()));
            bucket.taxable = bucket.taxable.add(TableRows.orZero(v.getTaxableValue()));
            bucket.igst = bucket.igst.add(TableRows.orZero(v.getIgst()));
            bucket.cgst = bucket.cgst.add(TableRows.orZero(v.getCgst()));
            bucket.sgst = bucket.sgst.add(TableRows.orZero(v.getSgst()));
            bucket.cess = bucket.cess.add(TableRows.orZero(v.getCess()));
            bucket.invoiceValue = bucket.invoiceValue.add(TableRows.orZero(v.getTotalValue()));
        }
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            Map<String, Object> payload = TableRows.payload();
            payload.put("hsn", bucket.hsn);
            payload.put("uqc", bucket.uqc);
            payload.put("quantity", bucket.quantity);
            payload.put("rate", bucket.rate);
            payload.put("supplyType", bucket.supplyType);
            payload.put("taxableValue", bucket.taxable);
            payload.put("igst", bucket.igst);
            payload.put("cgst", bucket.cgst);
            payload.put("sgst", bucket.sgst);
            payload.put("cess", bucket.cess);
            payload.put("invoiceValue", bucket.invoiceValue);
            rows.add(TableRows.row(workflow.getId(), "HSN",
                    "hsn:" + bucket.hsn + ":" + bucket.uqc + ":" + bucket.rate.toPlainString()
                            + ":" + bucket.supplyType.toLowerCase(),
                    null, payload));
        }
        return rows;
    }
}
