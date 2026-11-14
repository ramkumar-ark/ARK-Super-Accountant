package com.arktech.superaccountant.workflow.service;

/**
 * Thrown when the upload offered to use-upload does not exist or belongs to
 * another organization; the controller maps it to 404 {"error":"Upload not found"}.
 */
public class UploadNotFoundException extends RuntimeException {
    public UploadNotFoundException() {
        super("Upload not found");
    }
}
