package com.nhnacademy.insightonfront.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.adapter.auth.auth.RefreshCoordinator;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import com.nhnacademy.insightonfront.support.TestJwt;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AuthorizationRequestInterceptor 단위 테스트 — Feign 요청에 Authorization 헤더를 붙이고,
 * accessToken 이 만료 임박이면 코디네이터로 사전 갱신하는 규칙을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationRequestInterceptorTest {

    @Mock
    private ObjectProvider<RefreshCoordinator> coordinatorProvider;
    @Mock
    private RefreshCoordinator coordinator;

    @InjectMocks
    private AuthorizationRequestInterceptor interceptor;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        AccessTokenContext.clear();
    }

    private static RequestTemplate template(String path) {
        return new RequestTemplate().target("http://localhost").uri(path);
    }

    private static void bindRequest(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static String authHeader(RequestTemplate template) {
        Collection<String> values = template.headers().get("Authorization");
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    @Test
    @DisplayName("refresh 요청에는 Authorization 을 붙이지 않는다")
    void refreshRequest_noAuthorizationHeader() {
        bindRequest(new Cookie("accessToken", TestJwt.withExp(now() + 3600)));
        RequestTemplate template = template("/api/v1/auth/refresh");

        interceptor.apply(template);

        assertThat(authHeader(template)).isNull();
    }

    @Test
    @DisplayName("쿠키 accessToken 이 만료 임박이 아니면 그대로 헤더에 넣는다")
    void cookieToken_notExpiring_usedAsIs() {
        String token = TestJwt.withExp(now() + 3600);
        bindRequest(new Cookie("accessToken", token));
        RequestTemplate template = template("/api/v1/groups/my");

        interceptor.apply(template);

        assertThat(authHeader(template)).isEqualTo("Bearer " + token);
        verify(coordinator, never()).getRefreshedToken(any(), anyString());
    }

    @Test
    @DisplayName("쿠키 accessToken 이 만료 임박이면 미리 갱신한 토큰을 넣는다")
    void cookieToken_expiringSoon_proactivelyRefreshed() {
        bindRequest(
                new Cookie("accessToken", TestJwt.withExp(now() + 5)),
                new Cookie("userId", "7"),
                new Cookie("refreshToken", "RT"));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(anyLong(), anyString())).thenReturn("REFRESHED-AT");
        RequestTemplate template = template("/api/v1/groups/my");

        interceptor.apply(template);

        assertThat(authHeader(template)).isEqualTo("Bearer REFRESHED-AT");
        verify(coordinator, times(1)).getRefreshedToken(7L, "RT");
    }

    @Test
    @DisplayName("만료 임박이지만 갱신 정보가 부족하면 기존 토큰을 유지한다")
    void expiringSoon_insufficientInfo_keepsOldToken() {
        String token = TestJwt.withExp(now() + 5);
        bindRequest(new Cookie("accessToken", token)); // userId/refreshToken 쿠키 없음
        RequestTemplate template = template("/api/v1/groups/my");

        interceptor.apply(template);

        assertThat(authHeader(template)).isEqualTo("Bearer " + token);
        verify(coordinator, never()).getRefreshedToken(any(), anyString());
    }

    @Test
    @DisplayName("ThreadLocal 토큰이 있으면 만료 임박이어도 미리 갱신하지 않고 그대로 쓴다")
    void threadLocalToken_notProactivelyRefreshed() {
        String token = TestJwt.withExp(now() + 5);
        AccessTokenContext.set(token);
        RequestTemplate template = template("/api/v1/groups/my");

        interceptor.apply(template);

        assertThat(authHeader(template)).isEqualTo("Bearer " + token);
        verify(coordinator, never()).getRefreshedToken(any(), anyString());
    }

    @Test
    @DisplayName("토큰이 전혀 없으면 Authorization 을 붙이지 않는다")
    void noToken_noAuthorizationHeader() {
        bindRequest(); // 쿠키 없음
        RequestTemplate template = template("/api/v1/groups/my");

        interceptor.apply(template);

        assertThat(authHeader(template)).isNull();
    }
}
