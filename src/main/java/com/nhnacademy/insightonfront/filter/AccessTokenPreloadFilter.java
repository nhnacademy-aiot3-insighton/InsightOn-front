package com.nhnacademy.insightonfront.filter;

import com.nhnacademy.insightonfront.adapter.auth.auth.RefreshCoordinator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;


/**
 * 화면(페이지) 요청은 Feign 호출 없이 쿠키만 읽고 로그인 상태를 판단하는 경우가 있다
 * (예: HomeController). 이런 컨트롤러는 accessToken이 만료돼 브라우저가 쿠키를 지워버리면
 * refreshToken이 살아있어도 GUEST로 잘못 표시된다 — Feign 요청이 없어 기존
 * AuthorizationRequestInterceptor(사전 갱신)와 TokenRefreshErrorDecoder(사후 갱신)가
 * 아예 발동할 기회가 없기 때문이다.
 *
 * 이 필터는 그 사각지대만 메운다:
 *   - accessToken 쿠키가 "없을 때만" 동작 (있으면 즉시 통과, 비용 거의 없음)
 *   - refreshToken/userId 쿠키로 RefreshCoordinator를 통해 미리 갱신 시도
 *     (인터셉터가 쓰는 것과 동일한 single-flight 코디네이터 재사용)
 *   - 갱신 성공 시 응답에 직접 Set-Cookie를 추가해 브라우저 쿠키를 갱신하고,
 *     현재 요청(request)에도 즉시 반영해 뒤따르는 컨트롤러의
 *     @CookieValue("accessToken")가 이번 요청부터 바로 새 값을 보게 한다.
 *
 *     ⚠ AccessTokenCookieWriter(RequestContextHolder 기반)는 여기서 쓰지 않는다 —
 *     필터는 DispatcherServlet보다 앞단이라 RequestContextHolder가 아직
 *     세팅되지 않아 조용히 실패한다(로그로 확인됨: getRequestAttributes() == null).
 *     그래서 필터가 파라미터로 받은 response에 직접 Set-Cookie를 추가한다.
 *
 *   - 갱신 실패/정보 부족이면 조용히 다음 필터로 진행 (컨트롤러가 기존 로직대로 GUEST 처리)
 *     — 화면 렌더링 흐름을 절대 끊지 않는다.
 *
 * AccessTokenContextCleanupFilter(HIGHEST_PRECEDENCE)보다 뒤에 실행되도록
 * order를 한 단계 낮춰 둔다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class AccessTokenPreloadFilter extends OncePerRequestFilter {

    private static final Duration ACCESS_TOKEN_MAX_AGE = Duration.ofMinutes(15);

    private final ObjectProvider<RefreshCoordinator> coordinatorProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        log.info("[Filter-Debug] 요청 경로={}, accessToken={}",
                request.getRequestURI(), getCookie(request, "accessToken"));

        String accessToken = getCookie(request, "accessToken");

        // accessToken이 이미 있으면 이 필터가 할 일 없음 — 즉시 통과 (비용 거의 0)
        if (accessToken != null && !accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdStr = getCookie(request, "userId");
        String refreshToken = getCookie(request, "refreshToken");

        if (userIdStr == null || refreshToken == null) {
            // 정보 부족 → 갱신 시도 자체가 불가능, 컨트롤러가 GUEST로 처리하게 그대로 진행
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long userId = Long.valueOf(userIdStr);
            String newAccessToken = coordinatorProvider.getObject()
                    .getRefreshedToken(userId, refreshToken);

            if (Objects.nonNull(newAccessToken)) {
                // 브라우저용 쿠키 갱신 — response에 직접 씀 (RequestContextHolder 우회)
                writeAccessTokenCookie(request, response, newAccessToken);

                // 이번 요청 안에서도 뒤따르는 컨트롤러가 즉시 새 값을 보도록 request를 감쌈
                request = wrapWithAccessToken(request, newAccessToken);

                log.debug("[Auth] 페이지 진입 시 accessToken 사전 갱신 성공 - userId={}", userId);
            }
        } catch (Exception e) {
            log.debug("[Auth] 페이지 진입 시 accessToken 사전 갱신 실패 - userId={}, message={}",
                    userIdStr, e.getMessage());
            // 실패해도 예외를 던지지 않는다 — 화면은 그대로 렌더링되어야 하고,
            // 각 컨트롤러가 기존 로직대로 GUEST 등으로 처리한다.
        }

        filterChain.doFilter(request, response);
    }

    /** 필터가 직접 가진 response에 Set-Cookie 헤더를 추가한다 (RequestContextHolder 미사용). */
    private void writeAccessTokenCookie(HttpServletRequest request, HttpServletResponse response,
                                        String newAccessToken) {
        boolean secure = request.isSecure();
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("accessToken", newAccessToken)
                        .httpOnly(true).secure(secure).path("/").sameSite("Lax")
                        .maxAge(ACCESS_TOKEN_MAX_AGE)
                        .build().toString());
    }

    /** 갱신된 accessToken을 기존 쿠키 배열에 추가한 새 request로 감싼다. */
    private HttpServletRequest wrapWithAccessToken(HttpServletRequest original, String newAccessToken) {
        Cookie[] originalCookies = original.getCookies();
        Cookie[] merged;
        if (originalCookies == null) {
            merged = new Cookie[]{new Cookie("accessToken", newAccessToken)};
        } else {
            merged = Arrays.copyOf(originalCookies, originalCookies.length + 1);
            merged[originalCookies.length] = new Cookie("accessToken", newAccessToken);
        }

        Cookie[] finalCookies = merged;
        return new HttpServletRequestWrapper(original) {
            @Override
            public Cookie[] getCookies() {
                return finalCookies;
            }
        };
    }

    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}