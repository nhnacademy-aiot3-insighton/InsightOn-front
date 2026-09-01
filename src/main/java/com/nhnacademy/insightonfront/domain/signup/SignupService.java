package com.nhnacademy.insightonfront.domain.signup;

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

    public void sendEmailVerify(String email) {
        signupClient.sendEmailVerify(new EmailVerifyRequest(email));
    }

    public String confirmEmailVerify(String email, String code) {
        EmailVerifyConfirmResponse response =
                signupClient.emailCodeConfirm(new EmailVerifyConfirmRequest(email, code))
                        .getBody();
        return response != null ? response.verificationToken() : null;
    }

    public boolean checkEmailAvailable(String email) {
        EmailAvailableResponse response =
                signupClient.checkEmailAvailable(new EmailAvailableRequest(email)).getBody();
        return response != null && response.available();
    }

    public UserSignupResponse signup(String email, String password, String userName,
                                     String phoneNumber, String token) {
        return signupClient.doSignup(
                new UserSignupRequest(email, password, userName, phoneNumber, token)).getBody();
    }
}
