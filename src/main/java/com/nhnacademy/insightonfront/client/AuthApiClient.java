package com.nhnacademy.insightonfront.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 프론트 서버 → 인증 서버(게이트웨이) 호출 담당.
 * 브라우저가 아니라 프론트 "서버"가 서버-투-서버로 인증 서버를 호출한다. (BFF)
 * 앞서 얘기한 "서비스 → 서비스" 구간이라, 여기서 RestClient(동기)를 쓴다. Feign 으로 대체 가능.
 *
 * 인증 서버 로그인 응답:
 *   - body        = accessToken (문자열)
 *   - Set-Cookie  = refreshToken (httpOnly)  ← 서버-투-서버 호출이라 이 헤더가 프론트 서버로 온다
 */
@Component
public class AuthApiClient {

    private final RestClient restClient;

    public AuthApiClient(RestClient.Builder builder,
                         @Value("${service-url.gateway}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** 로그인 성공 시 accessToken + refreshToken 을 함께 돌려준다. 실패면 예외가 던져진다. */
    public LoginResult login(String email, String password) {
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginBody(email, password))   // UserLoginRequest 와 필드명 동일
                .retrieve()
                .toEntity(String.class);                // 4xx/5xx 면 HttpClientErrorException/HttpServerErrorException

        String accessToken = response.getBody();
        String refreshToken = extractCookie(response.getHeaders(), "refreshToken");
        return new LoginResult(accessToken, refreshToken);
    }

    /** Set-Cookie 헤더에서 특정 쿠키 값만 뽑아낸다. */
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

    /** 인증 서버로 보낼 JSON 바디. UserLoginRequest(email, password) 와 형태가 같아야 한다. */
    public record LoginBody(String email, String password) {}

    /** 로그인 결과 토큰 묶음. */
    public record LoginResult(String accessToken, String refreshToken) {}
}
