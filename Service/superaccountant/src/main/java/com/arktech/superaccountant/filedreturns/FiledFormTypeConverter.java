package com.arktech.superaccountant.filedreturns;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FiledFormTypeConverter implements AttributeConverter<FiledFormType, String> {
    @Override
    public String convertToDatabaseColumn(FiledFormType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public FiledFormType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FiledFormType.valueOf(dbData);
    }
}
