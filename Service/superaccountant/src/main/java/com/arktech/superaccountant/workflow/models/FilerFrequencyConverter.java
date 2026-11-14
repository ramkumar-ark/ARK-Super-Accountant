package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FilerFrequencyConverter implements AttributeConverter<FilerFrequency, String> {
    @Override
    public String convertToDatabaseColumn(FilerFrequency attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public FilerFrequency convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FilerFrequency.valueOf(dbData);
    }
}
