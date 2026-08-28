package com.nhnacademy.insightonfront.adapter.admin.dto;

import java.util.List;

public record AdminUserDetailResponse(
        Long userId,
        String email,
        String userName,
        String status,
        List<String> roles
) {
}
