package com.nhnacademy.insightonfront.adapter.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.nhnacademy.insightonfront.adapter.auth.auth.exception.SessionExpiredException;
import com.nhnacademy.insightonfront.auth.AccessTokenContext;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 * TokenRefreshErrorDecoder 단위 테스트 — 게이트웨이발 401(X-Auth-Error)에 대해
 * refresh 재시도(RetryableException) / 세션 만료(SessionExpiredException) / 기본 디코더 위임을
 * 각각 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class TokenRefreshErrorDecoderTest {

    private static final String METHOD_KEY = "AuthClient#call()";

    @Mock
    private ObjectProvider<RefreshCoordinator> coordinatorProvider;
    @Mock
    private RefreshCoordinator coordinator;

    @InjectMocks
    private TokenRefreshErrorDecoder decoder;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        AccessTokenContext.clear();
    }

    private static Response response(int status, Map<String, Collection<String>> headers) {
        Request request = Request.create(Request.HttpMethod.GET, "http://localhost/api/v1/test",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return Response.builder().status(status).reason("test").request(request).headers(headers).build();
    }

    private static Response response(int status, String authErrorCode) {
        return response(status, Map.of("X-Auth-Error", List.of(authErrorCode)));
    }

    private static void bindRequestWithCookies(Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(cookies);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("갱신 가능한 401인데 쿠키가 없으면 세션 만료")
    void refreshable401_noCookies_sessionExpired() {
        Exception result = decoder.decode(METHOD_KEY, response(401, "INVALID_TOKEN"));

        assertThat(result).isInstanceOf(SessionExpiredException.class);
    }

    @Test
    @DisplayName("갱신 가능한 401이고 쿠키가 있고 갱신에 성공하면 재시도 예외")
    void refreshable401_withCookies_refreshOk_retryable() {
        bindRequestWithCookies(new Cookie("userId", "7"), new Cookie("refreshToken", "RT"));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(anyLong(), anyString())).thenReturn("NEW-AT");

        Exception result = decoder.decode(METHOD_KEY, response(401, "INVALID_TOKEN"));

        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(AccessTokenContext.get()).isEqualTo("NEW-AT");
    }

    @Test
    @DisplayName("갱신 가능한 401이지만 갱신에 실패하면 세션 만료")
    void refreshable401_withCookies_refreshFails_sessionExpired() {
        bindRequestWithCookies(new Cookie("userId", "7"), new Cookie("refreshToken", "RT"));
        when(coordinatorProvider.getObject()).thenReturn(coordinator);
        when(coordinator.getRefreshedToken(anyLong(), anyString())).thenReturn(null);

        Exception result = decoder.decode(METHOD_KEY, response(401, "INVALID_TOKEN"));

        assertThat(result).isInstanceOf(SessionExpiredException.class);
    }

    @Test
    @DisplayName("MISSING_TOKEN 도 갱신 대상으로 취급한다")
    void missingToken_treatedAsRefreshable() {
        Exception result = decoder.decode(METHOD_KEY, response(401, "MISSING_TOKEN"));

        assertThat(result).isInstanceOf(SessionExpiredException.class); // 쿠키 없음 → 세션 만료
    }

    @Test
    @DisplayName("복구 불가 토큰 문제(TOKEN_REVOKED) 401은 세션 만료")
    void revokedToken_sessionExpired() {
        Exception result = decoder.decode(METHOD_KEY, response(401, "TOKEN_REVOKED"));

        assertThat(result).isInstanceOf(SessionExpiredException.class);
    }

    @Test
    @DisplayName("게이트웨이 헤더가 없는 401은 기본 디코더에 위임한다")
    void no401Header_delegatesToDefault() {
        Exception result = decoder.decode(METHOD_KEY, response(401, Map.of()));

        assertThat(result).isInstanceOf(FeignException.class);
        assertThat(result).isNotInstanceOf(SessionExpiredException.class);
        assertThat(((FeignException) result).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("비-401 응답은 기본 디코더에 위임한다")
    void non401_delegatesToDefault() {
        Exception result = decoder.decode(METHOD_KEY, response(500, "INVALID_TOKEN"));

        assertThat(result).isInstanceOf(FeignException.class);
        assertThat(result).isNotInstanceOf(SessionExpiredException.class);
        assertThat(((FeignException) result).status()).isEqualTo(500);
    }
}
