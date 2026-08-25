package com.nhnacademy.insightonfront.domain.mypage.dto;

import java.time.OffsetDateTime;

public record MyInfoResponse(
        String email,
        String userName,
        String phoneNumber,
        OffsetDateTime createdAt
) {
}
