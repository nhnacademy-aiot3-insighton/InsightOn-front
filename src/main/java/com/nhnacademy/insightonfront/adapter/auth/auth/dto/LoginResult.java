package com.nhnacademy.insightonfront.adapter.auth.auth.dto;

public record LoginResult(
        Long userId,
        String userName,
        Long groupId,
        String accessToken,
        String refreshToken
) {
}