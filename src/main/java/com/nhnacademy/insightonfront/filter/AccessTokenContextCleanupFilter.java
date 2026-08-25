package com.nhnacademy.insightonfront.filter;

import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// ThreadLocal 청소
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // 가장 바깥에서 감싸 요청 종료 시 확실히 정리
public class AccessTokenContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } finally {
            AccessTokenContext.clear();   // 어떤 경로로 set됐든 요청 끝나면 정리
        }
    }
}