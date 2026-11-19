package com.arktech.superaccountant.gstr1.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class Gstr1FillInAttributeConverter implements AttributeConverter<Gstr1FillInAttribute, String> {
    @Override
    public String convertToDatabaseColumn(Gstr1FillInAttribute attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Gstr1FillInAttribute convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Gstr1FillInAttribute.valueOf(dbData);
    }
}
