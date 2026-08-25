package com.nhnacademy.insightonfront.adapter.auth.auth.exception;

public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message) {
        super(message);
    }
}
