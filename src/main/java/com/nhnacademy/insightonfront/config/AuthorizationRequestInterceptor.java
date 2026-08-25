package com.nhnacademy.insightonfront.config;

import com.nhnacademy.insightonfront.adapter.auth.auth.RefreshCoordinator;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.util.AccessTokenCookieWriter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationRequestInterceptor implements RequestInterceptor {

    private final ObjectProvider<RefreshCoordinator> coordinatorProvider;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long EXPIRY_BUFFER_SEC = 30;   // 만료 30초 전이면 미리 갱신

    @Override
    public void apply(RequestTemplate template) {
        // refresh 요청은 accessToken/미리갱신과 무관 → 건너뜀 (재귀 차단)
        if (template.url().contains("/api/v1/auth/refresh")) {
            return;
        }

        String accessToken = resolveAccessToken();

        // 요청 발송 전 만료 임박 확인 → 미리 refresh
        // ThreadLocal 토큰(로그인 처리 / 재시도)은 대상 아님
        if (accessToken != null
                && AccessTokenContext.get() == null
                && isExpiringSoon(accessToken)) {
            String refreshed = proactiveRefresh();
            if (refreshed != null) {
                accessToken = refreshed;
            }
        }

        if (Objects.nonNull(accessToken)) {
            template.removeHeader("Authorization");
            template.header("Authorization", "Bearer " + accessToken);
        }
    }

    /** accessToken(JWT)의 exp를 디코드해 만료 임박(버퍼 이내)인지 확인 */
    private boolean isExpiringSoon(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode claims = OBJECT_MAPPER.readTree(payloadJson);
            long exp = claims.get("exp").asLong();
            long now = System.currentTimeMillis() / 1000;
            return exp - now < EXPIRY_BUFFER_SEC;
        } catch (Exception e) {
            log.debug("[Auth] exp 확인 실패 - 미리갱신 건너뜀", e);
            return false;   // 실패 시 미리갱신 안 함 (사후 대응이 처리)
        }
    }

    /** 만료 임박이면 코디네이터로 미리 refresh하고 쿠키도 갱신 */
    private String proactiveRefresh() {
        Long userId = getCookieAsLong("userId");
        String refreshToken = getCookie("refreshToken");
        if (userId == null || refreshToken == null) {
            return null;   // 정보 부족 → 미리갱신 포기 (사후 대응에 맡김)
        }
        String newToken = coordinatorProvider.getObject()
                .getRefreshedToken(userId, refreshToken);
        if (newToken != null) {
            AccessTokenCookieWriter.write(newToken);   // 브라우저 쿠키 갱신
            log.info("[Auth] 미리 갱신 성공 (userId={})", userId);
        }
        return newToken;
    }

    private String resolveAccessToken() {
        // 1순위: ThreadLocal (로그인 처리 / 재시도)
        String fromThreadLocal = AccessTokenContext.get();
        if (Objects.nonNull(fromThreadLocal)) {
            return fromThreadLocal;
        }
        // 2순위: accessToken 쿠키
        return getCookie("accessToken");
    }

    private String getCookie(String name) {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            Cookie[] cookies = request.getCookies();
            if (Objects.nonNull(cookies)) {
                for (Cookie cookie : cookies) {
                    if (name.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }

    private Long getCookieAsLong(String name) {
        String value = getCookie(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
