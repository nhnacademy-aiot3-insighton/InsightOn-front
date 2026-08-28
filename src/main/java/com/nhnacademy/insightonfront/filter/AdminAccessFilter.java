package com.nhnacademy.insightonfront.filter;

import com.nhnacademy.insightonfront.domain.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)   // Preload 필터 다음
@RequiredArgsConstructor
public class AdminAccessFilter extends OncePerRequestFilter {

    private final AuthService authService;   // hasAdminRole 메서드 보유

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // /admin/login, /admin/logout 등 로그인 자체는 제외
        boolean isAdminArea = path.startsWith("/admin/") && !path.equals("/admin/login");

        if (isAdminArea) {
            String accessToken = getCookie(request, "accessToken");

            if (accessToken == null || accessToken.isBlank()) {
                // 로그인 자체가 안 됨 → 관리자 로그인 페이지로
                response.sendRedirect("/admin/login");
                return;
            }

            if (!authService.hasAdminRole(accessToken)) {
                // 로그인은 했지만 ADMIN 아님 → 일반 메인으로
                response.sendRedirect("/");
                return;
            }
        }

        filterChain.doFilter(request, response);
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