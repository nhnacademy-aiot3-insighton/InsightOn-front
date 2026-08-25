package com.nhnacademy.insightonfront.domain.signup.dto;

public record EmailVerifyConfirmRequest(
        String email,
        String code
) {
}
