package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TallyChangeSourceTypeConverter implements AttributeConverter<TallyChangeSourceType, String> {
    @Override
    public String convertToDatabaseColumn(TallyChangeSourceType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TallyChangeSourceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TallyChangeSourceType.valueOf(dbData);
    }
}
