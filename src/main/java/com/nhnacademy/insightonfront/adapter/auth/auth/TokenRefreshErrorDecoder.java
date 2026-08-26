package com.nhnacademy.insightonfront.adapter.auth.auth;

import com.nhnacademy.insightonfront.adapter.auth.auth.exception.SessionExpiredException;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.util.AccessTokenCookieWriter;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Slf4j
public class TokenRefreshErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    // 순환 의존 방지를 위해 AuthClient를 지연 조회(ObjectProvider)
    // ★ AuthClient 대신 RefreshCoordinator를 지연 조회
    private final ObjectProvider<RefreshCoordinator> coordinatorProvider;

    public TokenRefreshErrorDecoder(ObjectProvider<RefreshCoordinator> coordinatorProvider) {
        this.coordinatorProvider = coordinatorProvider; // ★ AuthClient 자체가 아니라 "나중에 꺼낼 수 있는 통로"를 받음
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 401) {
            if (isInvalidToken(response)) {
                log.info("[Auth] accessToken 만료 감지 (method={}) → refresh 시도", methodKey);
                try {
                    String userIdStr = extractCookie("userId");
                    String refreshToken = extractCookie("refreshToken");
                    if (userIdStr == null || refreshToken == null) {
                        return new SessionExpiredException("재로그인이 필요합니다.");
                    }

                    // 코디네이터 경유 (미리갱신과 동일한 single-flight 사용)
                    String newAccessToken = coordinatorProvider.getObject()
                            .getRefreshedToken(Long.valueOf(userIdStr), refreshToken);

                    if (newAccessToken == null) {
                        return new SessionExpiredException("재로그인이 필요합니다.");
                    }

                    AccessTokenContext.set(newAccessToken);
                    AccessTokenCookieWriter.write(newAccessToken);

                    log.info("[Auth] refresh 완료 → 원요청 재시도");
                    return new RetryableException(
                            response.status(), "토큰 갱신 후 재시도",
                            response.request().httpMethod(), (Long) null, response.request());

                } catch (Exception e) {
                    log.warn("[Auth] refresh 중 예외 → 세션 만료", e);
                    return new SessionExpiredException("재로그인이 필요합니다.");
                }
            } else if (hasGatewayAuthErrorHeader(response)) {
                // MISSING_TOKEN, TOKEN_REVOKED, MISSING_JTI 등 게이트웨이가 감지한 토큰 문제 → 복구 불가
                log.warn("[Auth] 복구 불가 토큰 문제 (method={}) → 재로그인 필요", methodKey);
                return new SessionExpiredException("재로그인이 필요합니다.");
            }
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private boolean isInvalidToken(Response response) {
        return response.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("X-Auth-Error"))
                .flatMap(e -> e.getValue().stream())
                .anyMatch("INVALID_TOKEN"::equals);
    }

    /** 게이트웨이가 붙이는 X-Auth-Error 헤더가 있는지 확인 (게이트웨이발 401인지 판별) */
    private boolean hasGatewayAuthErrorHeader(Response response) {
        return response.headers().keySet().stream()
                .anyMatch(k -> k.equalsIgnoreCase("X-Auth-Error"));
    }

    private String extractCookie(String name) {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            Cookie[] cookies = attrs.getRequest().getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (name.equals(c.getName())) {
                        return c.getValue();
                    }
                }
            }
        }
        return null;
    }
}