package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TdsEngineFixtures {

    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID WF = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final LocalDate FY_START = LocalDate.of(2026, 4, 1);

    private TdsEngineFixtures() {
    }

    static ParsedVoucher voucher(String no, LocalDate date, String party, Object[][] rows) {
        ParsedVoucher v = new ParsedVoucher();
        v.setId(UUID.randomUUID());
        v.setVoucherNumber(no);
        v.setVoucherDate(date);
        v.setPartyLedgerName(party);
        v.setVoucherTypeName("Journal");
        v.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        for (Object[] row : rows) {
            ParsedVoucherEntry e = new ParsedVoucherEntry();
            e.setLedgerName((String) row[0]);
            BigDecimal amount = new BigDecimal((String) row[1]);
            e.setAmount(amount);
            e.setDebit(amount.signum() > 0);
            entries.add(e);
        }
        v.setEntries(entries);
        return v;
    }

    static UploadLedgerSnapshot snap(String name, LedgerCategory cat, Boolean tdsApplicable,
                                     String tdsSection, String pan) {
        return snap(name, cat, tdsApplicable, tdsSection, pan, null);
    }

    static UploadLedgerSnapshot snap(String name, LedgerCategory cat, Boolean tdsApplicable,
                                     String tdsSection, String pan, BigDecimal openingBalance) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setLedgerName(name);
        s.setCategory(cat);
        s.setTdsApplicable(tdsApplicable);
        s.setTdsSection(tdsSection);
        s.setPan(pan);
        s.setOpeningBalance(openingBalance);
        return s;
    }

    static TdsSectionConfig config(String code, String rateCo, String rateNonCo, String perTxn, String annual) {
        TdsSectionConfig c = new TdsSectionConfig();
        c.setSectionCode(code);
        c.setRateCompany(new BigDecimal(rateCo));
        c.setRateNonCompany(new BigDecimal(rateNonCo));
        c.setThresholdPerTransaction(perTxn == null ? null : new BigDecimal(perTxn));
        c.setThresholdAnnual(new BigDecimal(annual));
        c.setHigherRateIfNoPan(new BigDecimal("20.00"));
        return c;
    }

    static final Map<String, TdsSectionConfig> CONFIGS = buildConfigs();

    private static Map<String, TdsSectionConfig> buildConfigs() {
        Map<String, TdsSectionConfig> m = new LinkedHashMap<>();
        m.put("194C", config("194C", "2.00", "1.00", "30000.00", "100000.00"));
        m.put("194J_B", config("194J_B", "10.00", "10.00", null, "30000.00"));
        return Map.copyOf(m);
    }

    static Map<String, UploadLedgerSnapshot> stdSnaps(String pan) {
        Map<String, UploadLedgerSnapshot> m = new LinkedHashMap<>();
        UploadLedgerSnapshot exp = snap("Subcontractor Charges", LedgerCategory.EXPENSE, true, "194C", null);
        UploadLedgerSnapshot cred = snap("ABC Contractors", LedgerCategory.CREDITOR, null, null, pan);
        UploadLedgerSnapshot tds = snap("TDS Payable 194C", LedgerCategory.TDS, true, "194C", null);
        m.put(TdsEngine.key(exp.getLedgerName()), exp);
        m.put(TdsEngine.key(cred.getLedgerName()), cred);
        m.put(TdsEngine.key(tds.getLedgerName()), tds);
        return m;
    }

    static TdsAnalysisInput input(List<ParsedVoucher> vouchers, Map<String, UploadLedgerSnapshot> snaps,
                                  LocalDate periodStart, LocalDate periodEnd) {
        return new TdsAnalysisInput(ORG, WF, JOB, vouchers, snaps, CONFIGS,
                FY_START, periodStart, periodEnd);
    }

    static TdsAnalysisInput inputQ1(List<ParsedVoucher> vouchers, Map<String, UploadLedgerSnapshot> snaps) {
        return input(vouchers, snaps, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
    }
}
