package com.arktech.superaccountant.masters.services;

import org.springframework.http.HttpStatus;

/** Thrown when a finding override cannot be validated or applied; carries the HTTP status to return. */
public class FindingOverrideException extends RuntimeException {
    private final HttpStatus status;

    public FindingOverrideException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
