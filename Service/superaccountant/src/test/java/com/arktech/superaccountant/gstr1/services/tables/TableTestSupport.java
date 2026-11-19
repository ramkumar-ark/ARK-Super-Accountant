package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class TableTestSupport {

    protected Workflow workflow() {
        Workflow w = new Workflow();
        w.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        w.setOrganizationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        return w;
    }

    protected VoucherView.Builder voucher() {
        return new VoucherView.Builder()
                .supplierStateCode("29")
                .placeOfSupply("Karnataka")
                .voucherDate(LocalDate.of(2025, 4, 15));
    }

    protected static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    protected static BigDecimal num(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return (BigDecimal) value;
    }

    protected static void assertNum(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
