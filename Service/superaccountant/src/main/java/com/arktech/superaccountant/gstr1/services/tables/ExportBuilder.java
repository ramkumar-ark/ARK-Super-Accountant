package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExportBuilder {

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isNote(v.getVoucherTypeName())
                    || TableRows.hasGstin(v) || !isExportCandidate(v)) {
                continue;
            }
            String nature = TableRows.lower(v.getSupplyNature());
            String type = TableRows.lower(v.getTypeOfSupply());
            boolean withPayment = nature.contains("with payment") || type.contains("with payment");
            Map<String, Object> payload = TableRows.payload();
            payload.put("invoiceNo", v.getVoucherNumber());
            payload.put("invoiceDate", TableRows.date(v.getVoucherDate()));
            payload.put("exportType", withPayment ? "WP" : "WOP");
            payload.put("country", v.getConsigneeCountry());
            payload.put("rate", v.getRate());
            payload.put("taxableValue", TableRows.orZero(v.getTaxableValue()));
            payload.put("igst", TableRows.orZero(v.getIgst()));
            payload.put("cgst", TableRows.orZero(v.getCgst()));
            payload.put("sgst", TableRows.orZero(v.getSgst()));
            payload.put("cess", TableRows.orZero(v.getCess()));
            payload.put("invoiceValue", TableRows.orZero(v.getTotalValue()));
            rows.add(TableRows.row(workflow.getId(), "EXP", "exp:" + v.getVoucherKey(),
                    v.getVoucherKey(), payload));
        }
        return rows;
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
