package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class WorkflowEventKindConverter implements AttributeConverter<WorkflowEventKind, String> {
    @Override
    public String convertToDatabaseColumn(WorkflowEventKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public WorkflowEventKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WorkflowEventKind.valueOf(dbData);
    }
}
