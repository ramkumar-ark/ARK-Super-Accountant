package com.arktech.superaccountant.filedreturns;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FiledSourceKindConverter implements AttributeConverter<FiledSourceKind, String> {
    @Override
    public String convertToDatabaseColumn(FiledSourceKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public FiledSourceKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FiledSourceKind.valueOf(dbData);
    }
}
