package com.nhnacademy.insightonfront.config;

import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Component
public class AuthorizationRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String accessToken = resolveAccessToken();
        if (Objects.nonNull(accessToken)) {
            template.header("Authorization", "Bearer " + accessToken);
        }
    }

    private String resolveAccessToken() {
        // 1순위: 로그인 처리 도중처럼 서블릿 요청엔 아직 쿠키가 없는 경로에서 명시적으로 넘겨준 토큰
        String fromThreadLocal = AccessTokenContext.get();
        if (Objects.nonNull(fromThreadLocal)) {
            return fromThreadLocal;
        }

        // 2순위: 일반 HTTP 요청 - accessToken 쿠키에서 직접 읽음 (httpOnly라도 서버 코드는 읽을 수 있음)
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            Cookie[] cookies = request.getCookies();
            if (Objects.nonNull(cookies)) {
                for (Cookie cookie : cookies) {
                    if ("accessToken".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }

        return null;
    }
}
