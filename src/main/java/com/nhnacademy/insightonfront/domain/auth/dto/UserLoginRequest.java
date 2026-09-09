package com.nhnacademy.insightonfront.domain.auth.dto;

public record UserLoginRequest(
        String email,
        String password
) {

}
