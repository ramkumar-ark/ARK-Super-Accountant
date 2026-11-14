package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class CloseTypeConverter implements AttributeConverter<CloseType, String> {
    @Override
    public String convertToDatabaseColumn(CloseType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CloseType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CloseType.valueOf(dbData);
    }
}
