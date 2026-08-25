package com.nhnacademy.insightonfront.domain.signup;

import com.nhnacademy.insightonfront.adapter.auth.auth.dto.UserLoginResponse;
import com.nhnacademy.insightonfront.adapter.auth.signup.SignupClient;
import com.nhnacademy.insightonfront.adapter.auth.signup.dto.EmailAvailableResponse;
import com.nhnacademy.insightonfront.adapter.auth.signup.dto.EmailVerifyConfirmResponse;
import com.nhnacademy.insightonfront.adapter.auth.signup.dto.UserSignupResponse;
import com.nhnacademy.insightonfront.domain.signup.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private final SignupClient signupClient;

    /** 이메일 인증 코드 요청 */
    public void sendEmailVerify(String email) {
        log.info("[Signup] 이메일 인증 코드 요청: email={}", email);
        signupClient.sendEmailVerify(new EmailVerifyRequest(email));
    }

    /** 이메일 인증 코드 확인 → 검증된 토큰 반환 */
    public String confirmEmailVerify(String email, String code) {
        log.info("[Signup] 이메일 인증 코드 확인: email={}", email);
        EmailVerifyConfirmResponse response =
                signupClient.emailCodeConfirm(new EmailVerifyConfirmRequest(email, code))
                        .getBody();
        return response != null ? response.verificationToken() : null;
    }

    /** 이메일 중복 확인 */
    public boolean checkEmailAvailable(String email) {
        log.info("[Signup] 이메일 중복 확인: email={}", email);
        EmailAvailableResponse response =
                signupClient.checkEmailAvailable(new EmailAvailableRequest(email)).getBody();
        return response != null && response.available();
    }

    /** 회원가입 */
    public UserSignupResponse signup(String email, String password, String userName,
                                     String phoneNumber, String token) {
        log.info("[Signup] 회원가입 요청: email={}", email);
        UserSignupResponse response = signupClient.doSignup(
                new UserSignupRequest(email, password, userName, phoneNumber, token)).getBody();
        log.info("[Signup] 회원가입 성공: email={}", email);
        return response;
    }

    /** 계정 재활성화 */
    public UserLoginResponse reactivate(String reactiveToken) {
        log.info("[Signup] 계정 재활성화 요청");
        return signupClient.userReactive(new ReactiveRequest(reactiveToken)).getBody();
    }

    /** 재활성화 이메일 인증 요청 */
    public void requestReactivateEmailVerify(String email) {
        log.info("[Signup] 재활성화 이메일 인증 요청: email={}", email);
        signupClient.userReactivateRequest(new EmailVerifyRequest(email));
    }

    /** 재활성화 이메일 인증 확인 */
    public UserLoginResponse confirmReactivateEmailVerify(String email, String code) {
        log.info("[Signup] 재활성화 이메일 인증 확인: email={}", email);
        return signupClient.userReactiveConfirm(new EmailVerifyConfirmRequest(email, code)).getBody();
    }

    /** 이메일 찾기 */
    public String findEmail(String userName, String phoneNumber) {
        log.info("[Signup] 이메일 찾기 요청: userName={}", userName);
        return signupClient.findEmail(new FindEmailRequest(userName, phoneNumber)).getBody();
    }

    /** 비밀번호 재설정 요청 */
    public void requestPasswordReset(String email) {
        log.info("[Signup] 비밀번호 재설정 요청: email={}", email);
        signupClient.passwordReset(new PasswordResetRequest(email));
    }

    /** 비밀번호 재설정 확인 */
    public void confirmPasswordReset(String token, String password) {
        log.info("[Signup] 비밀번호 재설정 확인");
        signupClient.passwordResetConfirm(new PasswordResetConfirmRequest(token, password));
    }
}
