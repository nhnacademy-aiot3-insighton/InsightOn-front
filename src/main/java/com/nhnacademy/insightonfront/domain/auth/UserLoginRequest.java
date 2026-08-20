package com.nhnacademy.insightonfront.domain.auth;

public record UserLoginRequest(
        String email, String password
) {

}
