package com.nhnacademy.insightonfront.domain.signup.dto;


public record FindEmailRequest(
        String userName,
        String phoneNumber
) {
}
