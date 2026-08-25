package com.nhnacademy.insightonfront.domain.signup.dto;

public record UserLoginRequest(
        String email,

        String password
) {
    public UserLoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}