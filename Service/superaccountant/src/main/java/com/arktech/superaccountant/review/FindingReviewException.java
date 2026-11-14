package com.arktech.superaccountant.review;

/**
 * A review rule violation carrying the HTTP status to map the message to.
 */
public class FindingReviewException extends RuntimeException {
    private final int status;

    public FindingReviewException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
