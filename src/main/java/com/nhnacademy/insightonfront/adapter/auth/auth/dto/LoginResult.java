package com.nhnacademy.insightonfront.adapter.auth.auth.dto;

/**
 * 로그인 처리 결과. auth 응답의 status 를 그대로 들고 있어 컨트롤러가 분기한다.
 * <ul>
 *   <li>{@code SUCCESS} — accessToken/refreshToken 및 토큰에서 뽑은 사용자 정보가 채워진다.</li>
 *   <li>{@code PENDING_RESTORE} — 탈퇴 후 복구 가능 기간 내 계정. restoreToken 만 채워진다.</li>
 * </ul>
 */
public record LoginResult(
        String status,
        Long userId,
        String userName,
        Long groupId,
        String accessToken,
        String refreshToken,
        String restoreToken
) {

    public static LoginResult success(Long userId, String userName, Long groupId,
                                      String accessToken, String refreshToken) {
        return new LoginResult("SUCCESS", userId, userName, groupId, accessToken, refreshToken, null);
    }

    public static LoginResult pendingRestore(String restoreToken) {
        return new LoginResult("PENDING_RESTORE", null, null, null, null, null, restoreToken);
    }

    public boolean isPendingRestore() {
        return "PENDING_RESTORE".equals(status);
    }
}
