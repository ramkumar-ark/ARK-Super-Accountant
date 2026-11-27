package com.arktech.superaccountant.masters.validation;

import com.arktech.superaccountant.statutory.services.StatutorySectionService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validates a master's TDS section against the live statutory_sections table
 * (see {@link ValidTdsSection}). Sentinel values that are not sections — NOT_SUBJECT
 * and OTHER — stay accepted exactly as the former regex allowed them.
 */
public class ValidTdsSectionValidator implements ConstraintValidator<ValidTdsSection, String> {

    public static final String NOT_SUBJECT = "NOT_SUBJECT";

    public static final String OTHER = "OTHER";

    private final StatutorySectionService statutorySectionService;

    public ValidTdsSectionValidator(@Autowired StatutorySectionService statutorySectionService) {
        this.statutorySectionService = statutorySectionService;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || NOT_SUBJECT.equals(value) || OTHER.equals(value)) {
            return true;
        }
        return statutorySectionService.isActiveIncomeTaxSection(value);
    }
}
