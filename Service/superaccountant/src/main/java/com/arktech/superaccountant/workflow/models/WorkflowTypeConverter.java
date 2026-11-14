package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class WorkflowTypeConverter implements AttributeConverter<WorkflowType, String> {
    @Override
    public String convertToDatabaseColumn(WorkflowType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public WorkflowType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WorkflowType.valueOf(dbData);
    }
}
