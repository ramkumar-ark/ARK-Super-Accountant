package com.arktech.superaccountant.gst.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class VoucherSideConverter implements AttributeConverter<VoucherSide, String> {
    @Override
    public String convertToDatabaseColumn(VoucherSide attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public VoucherSide convertToEntityAttribute(String dbData) {
        return dbData == null ? null : VoucherSide.valueOf(dbData);
    }
}
