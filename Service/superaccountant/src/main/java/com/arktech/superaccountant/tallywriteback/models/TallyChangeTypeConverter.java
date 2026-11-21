package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TallyChangeTypeConverter implements AttributeConverter<TallyChangeType, String> {
    @Override
    public String convertToDatabaseColumn(TallyChangeType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TallyChangeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TallyChangeType.valueOf(dbData);
    }
}
