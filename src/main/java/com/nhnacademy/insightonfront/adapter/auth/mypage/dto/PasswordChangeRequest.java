package com.nhnacademy.insightonfront.adapter.auth.mypage.dto;

public record PasswordChangeRequest (
        String currentPassword,
        String newPassword
) {
}
