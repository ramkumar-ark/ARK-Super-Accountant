package com.arktech.superaccountant.filedreturns.parse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class Gstr3bRowLabels {

    record RowLabel(String rowCode, int expectedAmounts, List<String> labelPatterns) {
    }

    private Gstr3bRowLabels() {
    }

    static final Map<String, RowLabel> ROWS = List.of(
            new RowLabel("3.1a", 5, List.of(
                    "Outward taxable supplies \\(other than zero rated")),
            new RowLabel("3.1b", 5, List.of("Outward taxable supplies \\(zero rated\\)")),
            new RowLabel("3.1c", 5, List.of("Other outward supplies \\(Nil rated, exempted\\)")),
            new RowLabel("3.1d", 5, List.of("Inward supplies \\(liable to reverse charge\\)")),
            new RowLabel("3.1e", 5, List.of("Non-GST supplies")),
            new RowLabel("4A1", 4, List.of("Import of goods")),
            new RowLabel("4A2", 4, List.of("Import of services")),
            new RowLabel("4A3", 4, List.of("Inward supplies liable to reverse charge \\(other than \\d\\)")),
            new RowLabel("4A4", 4, List.of("ISD credit received")),
            new RowLabel("4A5", 4, List.of("All other ITC")),
            new RowLabel("4B1", 4, List.of("As per rules 38, 42, 43 and 55")),
            new RowLabel("4B2", 4, List.of("Others")),
            new RowLabel("4C", 4, List.of("Reclaimed \\| Deducted")),
            new RowLabel("4D1", 4, List.of("Inward supplies liable to reverse charge \\(other than \\d\\)")),
            new RowLabel("4D2", 4, List.of("ISD credit received but not distributed")),
            new RowLabel("5", 5, List.of(
                    "Outward taxable supplies \\(nil rated, exempted\\)",
                    "Composition suppliers, Merchant export")),
            new RowLabel("6.1-CASH", 4, List.of("Cash paid in utilisation")),
            new RowLabel("6.1-ITC", 4, List.of("ITC available in current tax period"))
    ).stream().collect(Collectors.toMap(RowLabel::rowCode, r -> r));
}
