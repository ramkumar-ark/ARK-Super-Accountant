package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.statutory.services.GstParameterSource;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class B2clBuilder {

    private final GstParameterSource parameterSource;

    public B2clBuilder(GstParameterSource parameterSource) {
        this.parameterSource = parameterSource;
    }

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        List<PreparedReturnRow> rows = new ArrayList<>();
        BigDecimal b2clThreshold = parameterSource.current().b2clThreshold(period(workflow));
        for (VoucherView v : sales) {
            if (v.isCancelled() || TableRows.isNote(v.getVoucherTypeName())
                    || TableRows.hasGstin(v) || isExportCandidate(v)) {
                continue;
            }
            String pos = v.placeOfSupplyCode();
            String supplier = v.getSupplierStateCode();
            if (pos == null || supplier == null || pos.equals(supplier)) {
                continue;
            }
            if (invoiceValue(v).compareTo(b2clThreshold) <= 0) {
                continue;
            }
            Map<String, Object> payload = TableRows.payload();
            payload.put("invoiceNo", v.getVoucherNumber());
            payload.put("invoiceDate", TableRows.date(v.getVoucherDate()));
            payload.put("pos", pos);
            payload.put("rate", v.getRate());
            payload.put("taxableValue", TableRows.orZero(v.getTaxableValue()));
            payload.put("igst", TableRows.orZero(v.getIgst()));
            payload.put("cgst", TableRows.orZero(v.getCgst()));
            payload.put("sgst", TableRows.orZero(v.getSgst()));
            payload.put("cess", TableRows.orZero(v.getCess()));
            payload.put("invoiceValue", invoiceValue(v));
            rows.add(TableRows.row(workflow.getId(), "B2CL", "b2cl:" + v.getVoucherKey(),
                    v.getVoucherKey(), payload));
        }
        return rows;
    }

    private java.time.YearMonth period(Workflow workflow) {
        return java.time.YearMonth.from(workflow.getPeriodStart());
    }

    private BigDecimal invoiceValue(VoucherView v) {
        return TableRows.orZero(v.getTotalValue());
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
