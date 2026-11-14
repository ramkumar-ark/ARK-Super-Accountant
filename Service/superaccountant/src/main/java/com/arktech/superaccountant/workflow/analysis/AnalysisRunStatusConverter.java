package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.WorkflowStageConverter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link AnalysisRunStatus} as varchar, following A's {@link WorkflowStageConverter} style.
 */
@Converter
public class AnalysisRunStatusConverter implements AttributeConverter<AnalysisRunStatus, String> {
    @Override
    public String convertToDatabaseColumn(AnalysisRunStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AnalysisRunStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AnalysisRunStatus.valueOf(dbData);
    }
}
