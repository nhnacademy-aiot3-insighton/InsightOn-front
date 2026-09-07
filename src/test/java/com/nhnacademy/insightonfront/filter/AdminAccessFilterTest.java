package com.nhnacademy.insightonfront.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.domain.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AdminAccessFilter 단위 테스트 — /admin/** 진입 시 accessToken 유무와 ADMIN 권한을 검사해
 * 로그인 페이지/일반 메인으로 리다이렉트하거나 통과시키는 규칙을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccessFilterTest {

    @Mock
    private AuthService authService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AdminAccessFilter filter;

    private static Cookie[] cookies(String name, String value) {
        return new Cookie[]{new Cookie(name, value)};
    }

    @Test
    @DisplayName("관리자 영역이 아니면 인증 검사 없이 통과")
    void nonAdminPath_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/mypage");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("/admin/login 은 검사에서 제외되어 통과")
    void adminLoginPath_excluded() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/login");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("관리자 영역인데 accessToken 쿠키가 없으면 /admin/login 으로 리다이렉트")
    void adminArea_noAccessToken_redirectsToAdminLogin() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/users");
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendRedirect("/admin/login");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("관리자 영역인데 accessToken 이 공백이면 /admin/login 으로 리다이렉트")
    void adminArea_blankAccessToken_redirectsToAdminLogin() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/users");
        when(request.getCookies()).thenReturn(cookies("accessToken", "   "));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendRedirect("/admin/login");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("로그인은 했지만 ADMIN 이 아니면 일반 메인(/)으로 리다이렉트")
    void adminArea_notAdmin_redirectsToRoot() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/users");
        when(request.getCookies()).thenReturn(cookies("accessToken", "user-token"));
        when(authService.hasAdminRole("user-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendRedirect("/");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("ADMIN 이면 통과")
    void adminArea_admin_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/users");
        when(request.getCookies()).thenReturn(cookies("accessToken", "admin-token"));
        when(authService.hasAdminRole("admin-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendRedirect(any());
    }
}
