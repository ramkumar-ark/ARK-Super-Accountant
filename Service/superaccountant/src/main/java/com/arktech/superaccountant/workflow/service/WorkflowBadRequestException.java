package com.arktech.superaccountant.workflow.service;

public class WorkflowBadRequestException extends RuntimeException {
    public WorkflowBadRequestException(String message) {
        super(message);
    }
}
