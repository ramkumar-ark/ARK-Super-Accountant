package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class B2bBuilder {

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isNote(v.getVoucherTypeName())
                    || !TableRows.hasGstin(v)) {
                continue;
            }
            Map<String, Object> payload = TableRows.payload();
            payload.put("gstin", v.getPartyGstin());
            payload.put("invoiceNo", v.getVoucherNumber());
            payload.put("invoiceDate", TableRows.date(v.getVoucherDate()));
            payload.put("pos", v.placeOfSupplyCode());
            payload.put("rate", v.getRate());
            payload.put("taxableValue", TableRows.orZero(v.getTaxableValue()));
            payload.put("igst", TableRows.orZero(v.getIgst()));
            payload.put("cgst", TableRows.orZero(v.getCgst()));
            payload.put("sgst", TableRows.orZero(v.getSgst()));
            payload.put("cess", TableRows.orZero(v.getCess()));
            payload.put("invoiceValue", TableRows.orZero(v.getTotalValue()));
            rows.add(TableRows.row(workflow.getId(), "B2B", "b2b:" + v.getVoucherKey(),
                    v.getVoucherKey(), payload));
        }
        return rows;
    }
}
