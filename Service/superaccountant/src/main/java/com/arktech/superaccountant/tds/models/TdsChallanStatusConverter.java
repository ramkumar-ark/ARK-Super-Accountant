package com.arktech.superaccountant.tds.models;

import jakarta.persistence.AttributeConverter;

public class TdsChallanStatusConverter implements AttributeConverter<TdsChallanStatus, String> {

    @Override
    public String convertToDatabaseColumn(TdsChallanStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TdsChallanStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TdsChallanStatus.valueOf(dbData);
    }
}
