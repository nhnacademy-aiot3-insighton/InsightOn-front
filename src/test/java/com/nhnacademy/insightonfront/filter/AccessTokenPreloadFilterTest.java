package com.nhnacademy.insightonfront.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.adapter.auth.auth.RefreshCoordinator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;

/**
 * AccessTokenPreloadFilter 단위 테스트 — accessToken 쿠키가 없을 때만 refreshToken/userId 로
 * 사전 갱신을 시도하고, 성공 시 Set-Cookie 추가 + 현재 요청에 즉시 반영하며,
 * 실패·정보부족은 화면 흐름을 끊지 않고 그대로 통과하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AccessTokenPreloadFilterTest {

    @Mock
    private ObjectProvider<RefreshCoordinator> coordinatorProvider;
    @Mock
    private RefreshCoordinator coordinator;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AccessTokenPreloadFilter filter;

    private static Cookie[] cookies(Cookie... cookies) {
        return cookies;
    }

    @Test
    @DisplayName("accessToken 쿠키가 있으면 아무 것도 하지 않고 통과")
    void hasAccessToken_passesThroughImmediately() throws Exception {
        when(request.getCookies()).thenReturn(cookies(new Cookie("accessToken", "tok")));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(coordinatorProvider, never()).getObject();
    }

    @Test
    @DisplayName("accessToken 도 없고 userId/refreshToken 도 없으면 그대로 통과")
    void noCookies_passesThrough() throws Exception {
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(coordinatorProvider, never()).getObject();
    }

    @Test
    @DisplayName("사전 갱신에 성공하면 Set-Cookie 를 추가하고 현재 요청에도 새 accessToken 을 반영한다")
    void refreshSucceeds_writesCookieAndWrapsRequest() throws Exception {
        when(request.getCookies()).thenReturn(cookies(
                new Cookie("userId", "7"),
                new Cookie("refreshToken", "RT")));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(7L, "RT")).thenReturn("NEW-AT");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("accessToken=NEW-AT"));

        ArgumentCaptor<HttpServletRequest> forwarded = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(forwarded.capture(), eq(response));
        boolean hasNewToken = Arrays.stream(forwarded.getValue().getCookies())
                .anyMatch(c -> c.getName().equals("accessToken") && c.getValue().equals("NEW-AT"));
        assertThat(hasNewToken).isTrue();
    }

    @Test
    @DisplayName("사전 갱신 결과가 null 이면 쿠키를 건드리지 않고 그대로 통과")
    void refreshReturnsNull_passesThroughUnchanged() throws Exception {
        when(request.getCookies()).thenReturn(cookies(
                new Cookie("userId", "7"),
                new Cookie("refreshToken", "RT")));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(7L, "RT")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response, never()).addHeader(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("사전 갱신 중 예외가 나도 화면 흐름을 끊지 않고 통과")
    void refreshThrows_stillPassesThrough() throws Exception {
        when(request.getCookies()).thenReturn(cookies(
                new Cookie("userId", "7"),
                new Cookie("refreshToken", "RT")));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(7L, "RT")).thenThrow(new RuntimeException("auth down"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response, never()).addHeader(any(), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("userId 쿠키가 숫자가 아니면 조용히 통과")
    void nonNumericUserId_passesThrough() throws Exception {
        when(request.getCookies()).thenReturn(cookies(
                new Cookie("userId", "not-a-number"),
                new Cookie("refreshToken", "RT")));

        filter.doFilterInternal(request, response, filterChain);

        verify(response, never()).addHeader(any(), any());
        verify(filterChain).doFilter(request, response);
    }
}
