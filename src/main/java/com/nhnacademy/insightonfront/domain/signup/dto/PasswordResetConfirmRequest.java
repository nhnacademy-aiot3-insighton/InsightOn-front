package com.nhnacademy.insightonfront.domain.signup.dto;

public record PasswordResetConfirmRequest(
        String password,
        String token
) {
}
