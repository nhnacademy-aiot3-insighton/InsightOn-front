package com.nhnacademy.insightonfront.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AccessTokenContextCleanupFilter 단위 테스트 — 요청 처리 중 어디서 set 됐든
 * 요청이 끝나면(정상/예외 무관) ThreadLocal 이 정리되는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AccessTokenContextCleanupFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final AccessTokenContextCleanupFilter filter = new AccessTokenContextCleanupFilter();

    @AfterEach
    void tearDown() {
        AccessTokenContext.clear();
    }

    @Test
    @DisplayName("요청 처리 중 set 된 토큰을 요청 종료 시 정리한다")
    void clearsAfterRequest() throws Exception {
        doAnswer(invocation -> {
            AccessTokenContext.set("during-request");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(AccessTokenContext.get()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("체인이 예외를 던져도 ThreadLocal 을 정리하고 예외는 전파한다")
    void clearsEvenWhenChainThrows() throws Exception {
        AccessTokenContext.set("stale");
        doThrow(new ServletException("boom")).when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(ServletException.class);

        assertThat(AccessTokenContext.get()).isNull();
    }
}
