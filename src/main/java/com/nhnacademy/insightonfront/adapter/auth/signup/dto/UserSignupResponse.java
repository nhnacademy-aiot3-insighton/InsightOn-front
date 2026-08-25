package com.nhnacademy.insightonfront.adapter.auth.signup.dto;

import java.time.OffsetDateTime;

public record UserSignupResponse (
        String email,
        String userName,
        String phoneNumber,
        OffsetDateTime createdAt
){
}
