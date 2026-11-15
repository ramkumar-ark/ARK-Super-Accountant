package com.arktech.superaccountant.tds.services;

public class TdsNotFoundException extends RuntimeException {

    public TdsNotFoundException(String message) {
        super(message);
    }
}
