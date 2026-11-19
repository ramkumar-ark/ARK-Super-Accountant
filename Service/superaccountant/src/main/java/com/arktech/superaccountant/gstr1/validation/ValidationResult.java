package com.arktech.superaccountant.gstr1.validation;

/**
 * One finding of the prepared-return validator. ERROR findings block Finalize;
 * WARNING findings are advisory (shown on the validation panel, never blocking).
 */
public record ValidationResult(String code, Severity severity, String tableCode,
                               String rowKey, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }
}
