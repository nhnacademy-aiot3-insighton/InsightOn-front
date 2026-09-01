package com.nhnacademy.insightonfront.adapter.auth.auth;

import com.nhnacademy.insightonfront.adapter.auth.auth.dto.TokenRefreshResponse;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.UserLoginResponse;
import com.nhnacademy.insightonfront.domain.auth.dto.UserLoginRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.EmailVerifyConfirmRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.EmailVerifyRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.FindEmailRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.PasswordResetConfirmRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.PasswordResetRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 프론트 서버 → 게이트웨이(→ 인증 서버) 로그인 호출.
 * name = 게이트웨이 서비스 ID. 게이트웨이가 /api/v1/auth/** 를 auth 로 라우팅한다.
 */
@FeignClient(
        name = "insighton-gateway",
        contextId = "authClient",
        url = "${service-url.gateway}",
        configuration = AuthClientConfig.class)
public interface AuthClient {

    @PostMapping(
            value = "/api/v1/auth/login",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest userLoginRequest);

    /**
     * refreshToken으로 새 accessToken 재발급.
     * [가정] refreshToken은 쿠키로 자동 전송, 새 accessToken은 응답 바디로 받음.
     * → 실제 auth 스펙에 맞게 조정 필요 (아래 주의사항 참고)
     */
    @PostMapping("/api/v1/auth/refresh")
    ResponseEntity<TokenRefreshResponse> refresh(@RequestHeader("Cookie") String cookieHeader);

    // ★ 로그아웃 추가
    @PostMapping("/api/v1/auth/logout")
    ResponseEntity<Void> logout();

    // 이메일 찾기
    @PostMapping("/api/v1/auth/find-email")
    ResponseEntity<String> findEmail(@RequestBody FindEmailRequest findEmailRequest);

    // 비밀번호 재설정 요청
    @PostMapping("/api/v1/auth/password/reset-request")
    ResponseEntity<Void> passwordReset(@RequestBody PasswordResetRequest passwordResetRequest);

    // 비밀번호 재설정 확인
    @PostMapping("/api/v1/auth/password/reset-confirm")
    ResponseEntity<Void> passwordResetConfirm(
            @RequestBody PasswordResetConfirmRequest passwordResetConfirmRequest);

    // 탈퇴 계정 재활성화 — 인증 코드 발송
    @PostMapping("/api/v1/auth/reactivate/email-verify-request")
    ResponseEntity<Void> reactivateRequest(@RequestBody EmailVerifyRequest emailVerifyRequest);

    // 탈퇴 계정 재활성화 — 인증 코드 확인 후 복구 + 로그인 (응답 규약은 /login 과 동일)
    @PostMapping("/api/v1/auth/reactivate/email-verify-confirm")
    ResponseEntity<UserLoginResponse> reactivateConfirm(
            @RequestBody EmailVerifyConfirmRequest emailVerifyConfirmRequest);
}