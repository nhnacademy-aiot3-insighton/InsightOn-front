package com.nhnacademy.insightonfront.adapter.auth.auth.dto;

public record UserLoginResponse(
        String accessToken,
        String refreshToken
) {

}