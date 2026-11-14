package com.arktech.superaccountant.tds.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TdsDeducteeTypeConverter implements AttributeConverter<TdsDeducteeType, String> {
    @Override
    public String convertToDatabaseColumn(TdsDeducteeType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TdsDeducteeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TdsDeducteeType.valueOf(dbData);
    }
}
