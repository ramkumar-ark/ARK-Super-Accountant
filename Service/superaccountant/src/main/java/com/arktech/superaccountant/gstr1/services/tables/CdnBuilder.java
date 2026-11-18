package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.Gstr1Rules;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CdnBuilder {

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        List<PreparedReturnRow> rows = new ArrayList<>();
        YearMonth period = YearMonth.from(workflow.getPeriodStart());
        for (VoucherView v : sales) {
            if (v.isCancelled() || !TableRows.isNote(v.getVoucherTypeName())) {
                continue;
            }
            boolean registered = TableRows.hasGstin(v);
            String tableCode = registered ? "CDNR" : "CDNUR";
            Map<String, Object> payload = TableRows.payload();
            payload.put("noteType", v.getVoucherTypeName());
            if (registered) {
                payload.put("gstin", v.getPartyGstin());
            } else {
                String pos = v.placeOfSupplyCode();
                String supplier = v.getSupplierStateCode();
                boolean interStateLarge = pos != null && supplier != null && !pos.equals(supplier)
                        && TableRows.orZero(v.getTotalValue())
                                .compareTo(Gstr1Rules.b2clThreshold(period)) > 0;
                payload.put("type", interStateLarge ? "B2CL" : "B2CS");
                payload.put("pos", pos);
            }
            payload.put("noteNo", v.getVoucherNumber());
            payload.put("noteDate", TableRows.date(v.getVoucherDate()));
            payload.put("rate", v.getRate());
            payload.put("taxableValue", negate(v.getTaxableValue()));
            payload.put("igst", negate(v.getIgst()));
            payload.put("cgst", negate(v.getCgst()));
            payload.put("sgst", negate(v.getSgst()));
            payload.put("cess", negate(v.getCess()));
            payload.put("invoiceValue", negate(v.getTotalValue()));
            rows.add(TableRows.row(workflow.getId(), tableCode, "cdn:" + v.getVoucherKey(),
                    v.getVoucherKey(), payload));
        }
        return rows;
    }

    private BigDecimal negate(BigDecimal value) {
        return TableRows.orZero(value).abs().negate();
    }
}
