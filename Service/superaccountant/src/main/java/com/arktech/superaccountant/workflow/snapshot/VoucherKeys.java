package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VoucherKeys {

    private VoucherKeys() {
    }

    public static String of(String guid, String tallyMasterId, String type, String number, LocalDate date) {
        if (guid != null && !guid.isBlank()) {
            return "G:" + guid;
        }
        if (tallyMasterId != null && !tallyMasterId.isBlank()) {
            return "M:" + tallyMasterId;
        }
        return "N:" + type + "|" + number + "|" + date;
    }

    public static String of(ParsedVoucher v) {
        return of(v.getVoucherGuid(), v.getTallyMasterId(), v.getVoucherTypeName(),
                v.getVoucherNumber(), v.getVoucherDate());
    }

    public static Map<String, ParsedVoucher> keyAll(List<ParsedVoucher> vouchers) {
        Map<String, List<ParsedVoucher>> byKey = new LinkedHashMap<>();
        for (ParsedVoucher v : vouchers) {
            byKey.computeIfAbsent(of(v), k -> new java.util.ArrayList<>()).add(v);
        }
        Map<String, ParsedVoucher> keyed = new LinkedHashMap<>();
        for (Map.Entry<String, List<ParsedVoucher>> entry : byKey.entrySet()) {
            List<ParsedVoucher> group = entry.getValue();
            if (group.size() == 1) {
                keyed.put(entry.getKey(), group.get(0));
            } else {
                List<ParsedVoucher> sorted = group.stream()
                        .sorted(Comparator.comparing(ParsedVoucher::getContentHash,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .collect(Collectors.toList());
                for (int i = 0; i < sorted.size(); i++) {
                    keyed.put(entry.getKey() + "#" + (i + 1), sorted.get(i));
                }
            }
        }
        return keyed;
    }
}
