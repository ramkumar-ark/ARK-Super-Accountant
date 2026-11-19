package com.arktech.superaccountant.gstr1.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class Gstr1FillInScopeConverter implements AttributeConverter<Gstr1FillInScope, String> {
    @Override
    public String convertToDatabaseColumn(Gstr1FillInScope attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Gstr1FillInScope convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Gstr1FillInScope.valueOf(dbData);
    }
}
