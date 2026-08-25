package com.nhnacademy.insightonfront.domain.signup.dto;

public record UserSignupRequest(
        String email,
        String password,
        String userName,
        String phoneNumber,
        String token
) {
    public UserSignupRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (userName != null) {
            userName = userName.trim();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
        }
    }
}