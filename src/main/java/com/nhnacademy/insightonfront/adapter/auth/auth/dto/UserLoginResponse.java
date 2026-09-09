package com.nhnacademy.insightonfront.adapter.auth.auth.dto;

/**
 * auth 의 로그인/관리자 로그인(doLogin) 응답 바디. auth 측 {@code LoginResponse} 와 동일 구조.
 * <ul>
 *   <li>{@code SUCCESS} — accessToken 유효, restoreToken 은 null. refresh 토큰은 Set-Cookie 로 별도 전달.</li>
 *   <li>{@code PENDING_RESTORE} — 탈퇴 후 복구 가능 기간(7일) 내 계정. 로그인 성공이 아니며
 *       accessToken 은 null, restoreToken 만 채워진다.</li>
 * </ul>
 */
public record UserLoginResponse(
        String status,
        String accessToken,
        String restoreToken
) {

    /** 탈퇴 후 복구 가능 기간 내 계정 — 로그인 성공이 아님. */
    public boolean isPendingRestore() {
        return "PENDING_RESTORE".equals(status);
    }
}
