package com.nhnacademy.insightonfront.domain.auth;

import com.nhnacademy.insightonfront.adapter.admin.AdminClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.AuthClient;
import com.nhnacademy.insightonfront.adapter.auth.auth.dto.LoginResult;
import com.nhnacademy.insightonfront.adapter.core.group.GroupClient;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.domain.auth.dto.UserLoginRequest;
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
    private final GroupClient groupClient;
    private final ObjectMapper objectMapper;

    /** 로그인 처리: auth 호출 → 토큰에서 정보 추출 → groupId 조회까지 수행. */
    public LoginResult login(String email, String password) {
        ResponseEntity<String> response = authClient.login(new UserLoginRequest(email, password));

        String accessToken = response.getBody();
        String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
        Long userId = extractUserId(accessToken);
        String userName = extractUserName(accessToken);
        Long groupId = resolveGroupId(accessToken);

        return new LoginResult(userId, userName, groupId, accessToken, refreshToken);
    }

    /** Admin 로그인 처리: auth 호출 → 토큰에서 정보 추출 → groupId 조회까지 수행. */
    public LoginResult loginAdmin(String email, String password) {
        ResponseEntity<String> response = adminClient.login(new UserLoginRequest(email, password));

        String accessToken = response.getBody();
        String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
        Long userId = extractUserId(accessToken);
        String userName = extractUserName(accessToken);

        return new LoginResult(userId, userName, null, accessToken, refreshToken);
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

    public void confirmPasswordReset(String token, String password) {
        authClient.passwordResetConfirm(new PasswordResetConfirmRequest(token, password));
    }
}
