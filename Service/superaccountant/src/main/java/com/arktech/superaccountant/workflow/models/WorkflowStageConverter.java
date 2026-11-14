package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class WorkflowStageConverter implements AttributeConverter<WorkflowStage, String> {
    @Override
    public String convertToDatabaseColumn(WorkflowStage attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public WorkflowStage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WorkflowStage.valueOf(dbData);
    }
}
