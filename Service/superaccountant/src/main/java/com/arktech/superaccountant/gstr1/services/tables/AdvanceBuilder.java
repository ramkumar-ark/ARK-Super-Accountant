package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AdvanceBuilder {

    private static final Set<String> AT_TYPES =
            Set.of("advance payment", "advance receipt");
    private static final Set<String> TXPD_TYPES =
            Set.of("advance adjustment");

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (VoucherView v : sales) {
            if (v.isCancelled()) {
                continue;
            }
            String type = lower(v.getVoucherTypeName());
            String tableCode;
            if (AT_TYPES.contains(type)) {
                tableCode = "AT";
            } else if (TXPD_TYPES.contains(type)) {
                tableCode = "TXPD";
            } else {
                continue;
            }
            BigDecimal advance = v.getTaxableValue() != null
                    ? v.getTaxableValue() : TableRows.orZero(v.getTotalValue());
            Map<String, Object> payload = TableRows.payload();
            payload.put("voucherNo", v.getVoucherNumber());
            payload.put("voucherDate", TableRows.date(v.getVoucherDate()));
            payload.put("pos", v.placeOfSupplyCode());
            payload.put("rate", v.getRate());
            payload.put("advanceAmount", advance);
            payload.put("igst", TableRows.orZero(v.getIgst()));
            payload.put("cgst", TableRows.orZero(v.getCgst()));
            payload.put("sgst", TableRows.orZero(v.getSgst()));
            payload.put("cess", TableRows.orZero(v.getCess()));
            rows.add(TableRows.row(workflow.getId(), tableCode,
                    tableCode.toLowerCase(Locale.ROOT) + ":" + v.getVoucherKey(),
                    v.getVoucherKey(), payload));
        }
        return rows;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
