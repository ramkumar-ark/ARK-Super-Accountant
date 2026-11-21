package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TallyChangeStatusConverter implements AttributeConverter<TallyChangeStatus, String> {
    @Override
    public String convertToDatabaseColumn(TallyChangeStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TallyChangeStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TallyChangeStatus.valueOf(dbData);
    }
}
