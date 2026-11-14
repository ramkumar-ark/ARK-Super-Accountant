package com.arktech.superaccountant.daybook.services;

import java.util.Map;

/**
 * Thrown by {@link DayBookIntakeService} when an upload is rejected; the controller
 * maps it directly to {@code ResponseEntity.status(status).body(body)}.
 */
public class DayBookIntakeException extends RuntimeException {

    private final int status;
    private final transient Map<String, Object> body;

    public DayBookIntakeException(int status, Map<String, Object> body) {
        super((String) body.get("error"));
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
