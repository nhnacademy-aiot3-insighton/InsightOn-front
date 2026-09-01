package com.nhnacademy.insightonfront.adapter.auth.signup;

import com.nhnacademy.insightonfront.adapter.auth.signup.dto.*;
import com.nhnacademy.insightonfront.domain.signup.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 프론트 서버 → 게이트웨이(→ 인증 서버) 회원가입/이메일 인증/재활성화 호출.
 * 로그인·refresh·로그아웃(AuthClient)과 분리 — 토큰이 필요 없는 가입 절차 전용.
 */
@FeignClient(
        name = "insighton-gateway",
        contextId = "signupClient",
        url = "${service-url.gateway}")
public interface SignupClient {

    // 이메일 인증 코드 요청
    @PostMapping("/api/v1/auth/email/verify-request")
    ResponseEntity<Void> sendEmailVerify(@RequestBody EmailVerifyRequest emailVerifyRequest);

    // 이메일 인증 코드 확인
    @PostMapping("/api/v1/auth/email/verify-confirm")
    ResponseEntity<EmailVerifyConfirmResponse> emailCodeConfirm(
            @RequestBody EmailVerifyConfirmRequest emailVerifyConfirmRequest);

    // 이메일 중복 확인
    @PostMapping("/api/v1/auth/check-email")
    ResponseEntity<EmailAvailableResponse> checkEmailAvailable(
            @RequestBody EmailAvailableRequest emailAvailableRequest);

    // 회원가입
    @PostMapping("/api/v1/auth/signup")
    ResponseEntity<UserSignupResponse> doSignup(@RequestBody UserSignupRequest userSignupRequest);
}