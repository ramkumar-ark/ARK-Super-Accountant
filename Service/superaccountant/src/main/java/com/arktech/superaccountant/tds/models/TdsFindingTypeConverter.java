package com.arktech.superaccountant.tds.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TdsFindingTypeConverter implements AttributeConverter<TdsFindingType, String> {
    @Override
    public String convertToDatabaseColumn(TdsFindingType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TdsFindingType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TdsFindingType.valueOf(dbData);
    }
}
