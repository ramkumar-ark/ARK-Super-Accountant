package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record NetFiledPosition(List<FiledDocument> documents, Map<B2csKey, HeadAmounts> b2csTotals,
                               Map<UUID, String> contentHashBySnapshot) {

    public record SnapshotWithLines(FiledReturnSnapshot snapshot, List<FiledReturnLine> lines) {
    }

    public record FiledDocument(String tableCode, DocKind kind, String counterpartyGstin, String docNumber,
                                String docKey, String looseKey, LocalDate docDate, HeadAmounts amounts,
                                Map<BigDecimal, BigDecimal> taxableByRate, UUID snapshotId,
                                String amendedInPeriod) {

        public LocalDate effectiveDate() {
            return docDate;
        }
    }

    public record B2csKey(BigDecimal rate, String supplyType) {
    }

    private static final Set<String> INVOICE_TABLES = Set.of("B2B", "CDNR", "B2CL", "EXP", "CDNUR");
    private static final Map<String, String> AMENDMENT_TABLES = Map.of(
            "B2BA", "B2B", "CDNRA", "CDNR", "B2CLA", "B2CL", "EXPA", "EXP", "CDNURA", "CDNUR");

    public static NetFiledPosition build(List<SnapshotWithLines> snapshotsInPeriodOrder,
                                         LocalDate coverStart, LocalDate coverEnd) {
        Map<String, Group> groups = new LinkedHashMap<>();
        List<Amendment> amendments = new ArrayList<>();
        Map<UUID, String> hashes = new HashMap<>();
        Map<B2csKey, HeadAmounts> b2cs = new LinkedHashMap<>();
        List<B2csAdjustment> b2csAdjustments = new ArrayList<>();

        for (SnapshotWithLines swl : snapshotsInPeriodOrder) {
            FiledReturnSnapshot snapshot = swl.snapshot();
            hashes.put(snapshot.getId(), snapshot.getContentHash());
            Map<B2csKey, HeadAmounts> snapshotB2cs = new LinkedHashMap<>();

            for (FiledReturnLine line : swl.lines()) {
                String table = line.getTableCode();
                if (table == null) {
                    continue;
                }
                if (INVOICE_TABLES.contains(table)) {
                    group(groups, table, line, snapshot.getId(), null);
                } else if (AMENDMENT_TABLES.containsKey(table)) {
                    amendments.add(new Amendment(AMENDMENT_TABLES.get(table), line, snapshot.getId(),
                            snapshot.getFilingPeriod()));
                } else if ("B2CS".equals(table)) {
                    snapshotB2cs.merge(key(line), heads(line), HeadAmounts::plus);
                } else if ("B2CSA".equals(table)) {
                    b2csAdjustments.add(new B2csAdjustment(key(line), line.getOriginalPeriod(), heads(line),
                            snapshot.getId()));
                }
            }

            for (Map.Entry<B2csKey, HeadAmounts> e : snapshotB2cs.entrySet()) {
                b2cs.merge(e.getKey(), e.getValue(), HeadAmounts::plus);
            }
            for (B2csAdjustment adj : b2csAdjustments) {
                if (adj.originalPeriod() != null && monthInCover(adj.originalPeriod(), coverStart, coverEnd)) {
                    b2cs.remove(adj.key());
                    b2cs.merge(adj.key(), adj.amounts(), HeadAmounts::plus);
                }
            }
            b2csAdjustments.clear();
        }

        for (Amendment a : amendments) {
            String origKey = DocKeys.normalise(a.line().getOriginalDocNumber());
            Group group = origKey == null ? null : groups.get(a.baseTable() + "|" + gstin(a.line()) + "|" + origKey);
            if (group != null) {
                group.amounts = heads(a.line());
                group.taxableByRate = new HashMap<>();
                addRate(group.taxableByRate, a.line());
                group.snapshotId = a.snapshotId();
                group.amendedInPeriod = a.filingPeriod();
                if (a.line().getOriginalDocDate() != null) {
                    group.originalDate = a.line().getOriginalDocDate();
                }
            } else {
                LocalDate originalDate = a.line().getOriginalDocDate() != null
                        ? a.line().getOriginalDocDate() : a.line().getDocDate();
                if (originalDate != null && !originalDate.isBefore(coverStart) && !originalDate.isAfter(coverEnd)) {
                    String docNumber = a.line().getOriginalDocNumber() != null
                            ? a.line().getOriginalDocNumber() : a.line().getDocNumber();
                    group = new Group(a.baseTable(), kindOf(a.line()), gstin(a.line()), docNumber,
                            a.line().getDocDate(), a.snapshotId());
                    group.amounts = heads(a.line());
                    group.taxableByRate = new HashMap<>();
                    addRate(group.taxableByRate, a.line());
                    group.amendedInPeriod = a.filingPeriod();
                    group.originalDate = originalDate;
                    groups.put(group.groupKey(), group);
                }
            }
        }

        List<FiledDocument> documents = new ArrayList<>();
        for (Group g : groups.values()) {
            LocalDate effective = g.amendedInPeriod != null ? g.originalDate : g.docDate;
            if (effective == null || effective.isBefore(coverStart) || effective.isAfter(coverEnd)) {
                continue;
            }
            documents.add(new FiledDocument(g.tableCode, g.kind, g.gstin, g.docNumber,
                    DocKeys.normalise(g.docNumber), DocKeys.loose(g.docNumber), g.docDate,
                    g.amounts, Map.copyOf(g.taxableByRate), g.snapshotId, g.amendedInPeriod));
        }
        return new NetFiledPosition(documents, Map.copyOf(b2cs), Map.copyOf(hashes));
    }

    public String contentHash(UUID snapshotId) {
        return contentHashBySnapshot.getOrDefault(snapshotId, "");
    }

    private static void group(Map<String, Group> groups, String table, FiledReturnLine line,
                              UUID snapshotId, LocalDate ignored) {
        String key = table + "|" + gstin(line) + "|" + DocKeys.normalise(line.getDocNumber());
        Group g = groups.get(key);
        if (g == null) {
            g = new Group(table, kindOf(line), gstin(line), line.getDocNumber(), line.getDocDate(), snapshotId);
            groups.put(key, g);
        }
        g.amounts = g.amounts.plus(heads(line));
        addRate(g.taxableByRate, line);
    }

    private static void addRate(Map<BigDecimal, BigDecimal> taxableByRate, FiledReturnLine line) {
        if (line.getRate() == null) {
            return;
        }
        taxableByRate.merge(line.getRate(), line.getTaxable() == null ? BigDecimal.ZERO : line.getTaxable(),
                BigDecimal::add);
    }

    private static HeadAmounts heads(FiledReturnLine line) {
        return new HeadAmounts(line.getTaxable(), line.getIgst(), line.getCgst(), line.getSgst(), line.getCess());
    }

    private static DocKind kindOf(FiledReturnLine line) {
        String type = line.getDocType();
        if ("CN".equalsIgnoreCase(type)) {
            return DocKind.CN;
        }
        if ("DN".equalsIgnoreCase(type)) {
            return DocKind.DN;
        }
        return DocKind.INV;
    }

    private static String gstin(FiledReturnLine line) {
        return line.getCounterpartyGstin() == null ? "" : line.getCounterpartyGstin();
    }

    private static B2csKey key(FiledReturnLine line) {
        return new B2csKey(line.getRate(), line.getSupplyType() == null ? "" : line.getSupplyType());
    }

    private static boolean monthInCover(String period, LocalDate coverStart, LocalDate coverEnd) {
        try {
            YearMonth ym = YearMonth.parse(period);
            return !ym.isBefore(YearMonth.from(coverStart)) && !ym.isAfter(YearMonth.from(coverEnd));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class Group {
        private final String tableCode;
        private final DocKind kind;
        private final String gstin;
        private final String docNumber;
        private final LocalDate docDate;
        private UUID snapshotId;
        private HeadAmounts amounts = HeadAmounts.ZERO;
        private Map<BigDecimal, BigDecimal> taxableByRate = new HashMap<>();
        private String amendedInPeriod;
        private LocalDate originalDate;

        private Group(String tableCode, DocKind kind, String gstin, String docNumber, LocalDate docDate,
                      UUID snapshotId) {
            this.tableCode = tableCode;
            this.kind = kind;
            this.gstin = gstin;
            this.docNumber = docNumber;
            this.docDate = docDate;
            this.snapshotId = snapshotId;
        }

        private String groupKey() {
            return tableCode + "|" + gstin + "|" + DocKeys.normalise(docNumber);
        }
    }

    private record Amendment(String baseTable, FiledReturnLine line, UUID snapshotId, String filingPeriod) {
    }

    private record B2csAdjustment(B2csKey key, String originalPeriod, HeadAmounts amounts, UUID snapshotId) {
    }
}
