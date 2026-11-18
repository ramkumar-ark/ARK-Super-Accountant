package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class DocsIssuedBuilder {

    private static final class Doc {
        String number;
        boolean cancelled;
    }

    private record SeriesGroup(String voucherTypeName, TreeMap<Long, Doc> numbered) { }

    public List<PreparedReturnRow> build(Workflow workflow, List<VoucherView> sales) {
        Map<String, SeriesGroup> series = new LinkedHashMap<>();
        for (VoucherView v : sales) {
            String number = v.getVoucherNumber();
            if (TableRows.isBlank(number)) {
                continue;
            }
            Series seriesKey = parse(number.trim());
            if (seriesKey == null) {
                continue;
            }
            Doc doc = new Doc();
            doc.number = number.trim();
            doc.cancelled = v.isCancelled();
            String key = seriesKey(v.getVoucherTypeName(), seriesKey);
            series.computeIfAbsent(key, k -> new SeriesGroup(v.getVoucherTypeName(), new TreeMap<>()))
                    .numbered().put(seriesKey.numeric, doc);
        }
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (Map.Entry<String, SeriesGroup> entry : series.entrySet()) {
            List<List<Doc>> runs = consecutiveRuns(entry.getValue().numbered());
            for (List<Doc> run : runs) {
                Doc from = run.get(0);
                Doc to = run.get(run.size() - 1);
                long cancelled = run.stream().filter(d -> d.cancelled).count();
                Map<String, Object> payload = TableRows.payload();
                payload.put("voucherType", entry.getValue().voucherTypeName());
                payload.put("from", from.number);
                payload.put("to", to.number);
                payload.put("total", run.size());
                payload.put("cancelled", cancelled);
                rows.add(TableRows.row(workflow.getId(), "DOCS",
                        "docs:" + entry.getKey() + ":" + from.number + "-" + to.number,
                        null, payload));
            }
        }
        return rows;
    }

    private String seriesKey(String voucherTypeName, Series s) {
        return TableRows.lower(voucherTypeName) + ":" + s.prefix + "#" + s.width;
    }

    private List<List<Doc>> consecutiveRuns(TreeMap<Long, Doc> numbered) {
        List<List<Doc>> runs = new ArrayList<>();
        List<Doc> run = new ArrayList<>();
        Long previous = null;
        for (Map.Entry<Long, Doc> e : numbered.entrySet()) {
            if (previous != null && e.getKey() != previous + 1) {
                runs.add(run);
                run = new ArrayList<>();
            }
            run.add(e.getValue());
            previous = e.getKey();
        }
        if (!run.isEmpty()) {
            runs.add(run);
        }
        return runs;
    }

    private Series parse(String number) {
        int digitsStart = number.length();
        while (digitsStart > 0 && Character.isDigit(number.charAt(digitsStart - 1))) {
            digitsStart--;
        }
        if (digitsStart == number.length()) {
            return null;
        }
        Series s = new Series();
        s.prefix = number.substring(0, digitsStart);
        s.numeric = Long.parseLong(number.substring(digitsStart));
        s.width = number.length() - digitsStart;
        return s;
    }

    private static final class Series {
        String prefix;
        long numeric;
        int width;
    }
}
