package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TamperChangeKindConverter implements AttributeConverter<TamperChangeKind, String> {
    @Override
    public String convertToDatabaseColumn(TamperChangeKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TamperChangeKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TamperChangeKind.valueOf(dbData);
    }
}
