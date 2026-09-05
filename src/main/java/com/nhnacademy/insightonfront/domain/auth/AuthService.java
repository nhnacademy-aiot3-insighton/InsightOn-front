package com.nhnacademy.insightonfront.domain.auth;

import com.nhnacademy.insightonfront.adapter.admin.AdminClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.AuthClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.UserLoginResponse;
import com.nhnacademy.insightonfront.adapter.auth.signup.SignupClient;
import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.domain.auth.dto.UserLoginRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.EmailVerifyConfirmRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.EmailVerifyRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.FindEmailRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.PasswordResetConfirmRequest;
import com.nhnacademy.insightonfront.domain.signup.dto.PasswordResetRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final AuthClient authClient;
    private final AdminClient adminClient;
    private final SignupClient signupClient;
    private final GroupClient groupClient;
    private final ObjectMapper objectMapper;

    /** 로그인 처리: auth 호출 → 토큰에서 정보 추출 → groupId 조회까지 수행. */
    public LoginResult login(String email, String password) {
        ResponseEntity<UserLoginResponse> response = authClient.login(new UserLoginRequest(email, password));
        return buildUserLoginResult(response);
    }

    /**
     * 소셜 로그인 완료 처리: authorize·code교환·토큰발급은 auth 가 끝냈고 accessToken/refreshToken
     * 쿠키도 auth 가 심었다. 그 accessToken 으로 나머지 로그인 정보(userId/userName/groupId)만 채운다.
     */
    public LoginResult hydrateFromAccessToken(String accessToken, String refreshToken) {
        Long userId = extractUserId(accessToken);
        String userName = extractUserName(accessToken);
        Long groupId = resolveGroupId(accessToken);
        return LoginResult.success(userId, userName, groupId, accessToken, refreshToken);
    }

    /** 재활성화 인증 코드 발송 요청. 성공/실패(쿨다운·잠금)는 FeignException 으로 전파된다. */
    public void requestReactivateEmailVerify(String email) {
        authClient.reactivateRequest(new EmailVerifyRequest(email));
    }

    /**
     * 재활성화 인증 코드 확인 → auth 가 계정을 복구하고 로그인 응답(/login 과 동일 규약)을 돌려준다.
     * 이후 처리는 일반 로그인과 완전히 동일.
     */
    public LoginResult reactivateConfirm(String email, String code) {
        ResponseEntity<UserLoginResponse> response =
                authClient.reactivateConfirm(new EmailVerifyConfirmRequest(email, code));
        return buildUserLoginResult(response);
    }

    /** 일반 회원 로그인 응답(accessToken 바디 + refreshToken 쿠키)을 LoginResult 로 변환. */
    private LoginResult buildUserLoginResult(ResponseEntity<UserLoginResponse> response) {
        UserLoginResponse body = requireBody(response.getBody());

        if (body.isPendingRestore()) {
            return LoginResult.pendingRestore(body.restoreToken());
        }

        String accessToken = body.accessToken();
        String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
        Long userId = extractUserId(accessToken);
        String userName = extractUserName(accessToken);
        Long groupId = resolveGroupId(accessToken);

        return LoginResult.success(userId, userName, groupId, accessToken, refreshToken);
    }

    /** Admin 로그인 처리: auth 호출 → 토큰에서 정보 추출 → groupId 조회까지 수행. */
    public LoginResult loginAdmin(String email, String password) {
        ResponseEntity<UserLoginResponse> response = adminClient.login(new UserLoginRequest(email, password));
        UserLoginResponse body = requireBody(response.getBody());

        if (body.isPendingRestore()) {
            return LoginResult.pendingRestore(body.restoreToken());
        }

        String accessToken = body.accessToken();
        String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
        Long userId = extractUserId(accessToken);
        String userName = extractUserName(accessToken);

        return LoginResult.success(userId, userName, null, accessToken, refreshToken);
    }

    private UserLoginResponse requireBody(UserLoginResponse body) {
        if (body == null) {
            throw new IllegalStateException("로그인 응답 본문이 비어 있습니다.");
        }
        return body;
    }


    /** 로그아웃: auth에 토큰 무효화 요청. 실패해도 예외를 던지지 않고 로그만 남긴다(로그아웃 자체는 계속 진행). */
    public void logout() {
        try {
            authClient.logout();
        } catch (Exception e) {
            log.warn("[Auth] 로그아웃 실패: {}", e.getMessage());
        }
    }

    private Long extractUserId(String accessToken) {
        try {
            String payload = accessToken.split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(json);
            return claims.has("sub") ? claims.get("sub").asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserName(String accessToken) {
        try {
            String payload = accessToken.split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(json);
            return claims.has("name") ? claims.get("name").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveGroupId(String accessToken) {
        try {
            AccessTokenContext.set(accessToken);
            return groupClient.getMyGroupId().groupId();
        } catch (FeignException.NotFound e) {
            return null;
        } finally {
            AccessTokenContext.clear();
        }
    }

    private String extractCookie(HttpHeaders headers, String name) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        String prefix = name + "=";
        for (String cookie : setCookies) {
            for (String part : cookie.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(prefix)) {
                    return trimmed.substring(prefix.length());
                }
            }
        }
        return null;
    }

    public String findEmail(String userName, String phoneNumber) {
        return authClient.findEmail(new FindEmailRequest(userName, phoneNumber)).getBody();
    }

    public void requestPasswordReset(String email) {
        authClient.passwordReset(new PasswordResetRequest(email));
    }

    public void confirmPasswordReset(String password, String token) {
        authClient.passwordResetConfirm(new PasswordResetConfirmRequest(password, token));
    }

    public boolean hasAdminRole(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        try {
            String payload = accessToken.split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(json);

            if (!claims.has("roles") || !claims.get("roles").isArray()) {
                return false;
            }
            for (JsonNode role : claims.get("roles")) {
                if ("ADMIN".equals(role.asString())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("[Auth] accessToken role 파싱 실패 - 관리자 아님으로 처리: {}", e.getMessage());
            return false;
        }
    }
}
