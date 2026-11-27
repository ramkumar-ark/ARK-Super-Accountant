package com.arktech.superaccountant.masters.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A TDS section code a master may carry: null, the NOT_SUBJECT / OTHER sentinels, or
 * an active section row in statutory_sections. Replaces the hard-coded regex
 * allow-list so a seeded section is accepted everywhere and an unseeded one is
 * rejected everywhere without a code change.
 */
@Constraint(validatedBy = ValidTdsSectionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTdsSection {

    String message() default "Invalid TDS section code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
