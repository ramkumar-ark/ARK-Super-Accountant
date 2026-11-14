package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzerCatalogTest {

    @Test
    void hasAnalyzer_noBeans_false() {
        AnalyzerCatalog catalog = new AnalyzerCatalog(List.of());

        for (WorkflowType type : WorkflowType.values()) {
            assertThat(catalog.hasAnalyzer(type)).isFalse();
        }
    }

    @Test
    void hasAnalyzer_matchingType_true() {
        WorkflowAnalyzer gstOnly = new WorkflowAnalyzer() {
            @Override
            public String code() {
                return "GST_STUB";
            }

            @Override
            public Set<WorkflowType> types() {
                return Set.of(WorkflowType.GSTR1);
            }

            @Override
            public void analyze(AnalysisContext ctx) {
            }
        };

        AnalyzerCatalog catalog = new AnalyzerCatalog(List.of(gstOnly));

        assertThat(catalog.hasAnalyzer(WorkflowType.GSTR1)).isTrue();
        assertThat(catalog.hasAnalyzer(WorkflowType.GSTR3B)).isFalse();
    }
}
