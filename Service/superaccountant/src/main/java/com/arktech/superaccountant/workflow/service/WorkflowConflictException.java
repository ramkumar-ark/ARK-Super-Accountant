package com.arktech.superaccountant.workflow.service;

public class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException(String message) {
        super(message);
    }
}
