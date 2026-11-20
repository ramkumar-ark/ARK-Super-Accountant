package com.arktech.superaccountant.gstr1.channel;

/**
 * Result of asking a filing channel to submit a prepared return
 * (ADR-0002: the channel only submits; a portal upload is an event, not a stage).
 */
public record ChannelResult(Status status, String reference, String message) {

    public enum Status {
        SUBMITTED,
        MANUAL_UPLOAD_REQUIRED
    }
}
