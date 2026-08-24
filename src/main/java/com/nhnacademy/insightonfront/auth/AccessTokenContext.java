package com.nhnacademy.insightonfront.auth;

public final class AccessTokenContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private AccessTokenContext() {
    }

    public static void set(String accessToken) {
        CURRENT.set(accessToken);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
