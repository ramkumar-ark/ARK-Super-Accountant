package com.arktech.superaccountant.statutory.controllers;

import org.springframework.http.HttpStatus;

/** Carries an HTTP status with a business error from the statutory admin API. */
public class StatutoryAdminException extends RuntimeException {

    private final HttpStatus status;

    public StatutoryAdminException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
