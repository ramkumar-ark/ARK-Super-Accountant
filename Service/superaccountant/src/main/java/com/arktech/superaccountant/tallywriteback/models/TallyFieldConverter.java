package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TallyFieldConverter implements AttributeConverter<TallyField, String> {
    @Override
    public String convertToDatabaseColumn(TallyField attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TallyField convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TallyField.valueOf(dbData);
    }
}
