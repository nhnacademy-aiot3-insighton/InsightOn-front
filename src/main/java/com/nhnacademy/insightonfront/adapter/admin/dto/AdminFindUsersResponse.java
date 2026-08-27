package com.nhnacademy.insightonfront.adapter.admin.dto;

import java.time.OffsetDateTime;

public record AdminFindUsersResponse(
        Long userId,
        String email,
        String userName,
        String status,
        OffsetDateTime createdAt
) {
}