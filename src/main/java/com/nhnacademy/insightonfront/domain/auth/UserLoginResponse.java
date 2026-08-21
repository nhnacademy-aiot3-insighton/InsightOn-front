package com.nhnacademy.insightonfront.domain.auth;

public record UserLoginResponse(
        String accessToken,
        String refreshToken
) {

}