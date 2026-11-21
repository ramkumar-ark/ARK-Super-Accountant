package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PanFormatRuleTest {

    private static final UUID ORG = UUID.randomUUID();
    private final PanOverrideGateway gateway = mock(PanOverrideGateway.class);
    private final PanFormatRule rule = new PanFormatRule(gateway);
    private final ValidationContext context =
            new ValidationContext(ORG, "uploader", List.of(), Map.of());

    private ParsedLedger ledger(String name, String pan) {
        return ParsedLedger.builder()
                .name(name)
                .category(LedgerCategory.EXPENSE)
                .pan(pan)
                .build();
    }

    @Test
    void raisesHighOpenFindingForOverLengthPan() {
        List<ValidationFinding> findings = rule.execute(context,
                List.of(ledger("ANANDHA TIMBER DEPOT", " AAPPK2262D1 ")));

        assertThat(findings).hasSize(1);
        ValidationFinding f = findings.get(0);
        assertThat(f.getRuleCode()).isEqualTo(PanFormat.RULE_CODE);
        assertThat(f.getLedgerName()).isEqualTo("ANANDHA TIMBER DEPOT");
        assertThat(f.getCategory()).isEqualTo(LedgerCategory.EXPENSE);
        assertThat(f.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(f.getActualValue()).isEqualTo("AAPPK2262D1");
        assertThat(f.getUploadJobId()).isNull();
        assertThat(f.getMessage()).contains("AAPPK2262D1");
    }

    @Test
    void skipsValidBlankNullOrMissingBalances() {
        List<ValidationFinding> findings = rule.execute(context, List.of(
                ledger("VALID", "AAPPK2262D"),
                ledger("BLANK", "   "),
                ledger("NULL", null)));

        assertThat(findings).isEmpty();
    }

    @Test
    void flagsLowercaseShapedPanBecauseTallyValueIsInvalid() {
        assertThat(rule.execute(context, List.of(ledger("LOWER", "aappk2262d")))).hasSize(1);
    }

    @Test
    void suppressesLedgerThatAlreadyHasCompanyOverride() {
        when(gateway.findPan(ORG, "ANANDHA TIMBER DEPOT")).thenReturn(Optional.of("AAPPK2262D"));

        assertThat(rule.execute(context, List.of(ledger("ANANDHA TIMBER DEPOT", "AAPPK2262D1"))))
                .isEmpty();
    }
}
